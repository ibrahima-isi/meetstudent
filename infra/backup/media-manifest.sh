# Shared media inventory & checksum helpers for backup.sh and restore.sh.
# Sourced by both scripts; relies only on POSIX sh, BusyBox find/stat/sha256sum/od/awk.

manifest_hex_encode() {
  printf '%s' "$1" | od -An -tx1 -v | tr -d ' \n'
}

# Scan a directory tree into TSV: <key_hex>\t<size_bytes>\t<sha256>
# Ignores symlinks (only -type f) and in-flight DiskMediaStorage temp files (.media-*.tmp).
manifest_scan_tree() {
  root=$1
  prefix=$2
  out=$3
  : > "$out"
  [ -d "$root" ] || return 0
  find "$root" -type f ! -name '.media-*.tmp' -exec sh -c '
    root=$1
    prefix=$2
    shift 2
    for f do
      [ -f "$f" ] || exit 1
      rel=${f#"$root"/}
      key=$prefix$rel
      size=$(stat -c "%s" "$f") || exit 1
      sum=$(sha256sum "$f" | awk "{print \$1}") || exit 1
      [ -n "$sum" ] || exit 1
      hex=$(printf "%s" "$key" | od -An -tx1 -v | tr -d " \n")
      printf "%s\t%s\t%s\n" "$hex" "$size" "$sum"
    done
  ' _ "$root" "$prefix" {} + > "$out"
}

# Scan both unpacked/staged trees (uploads_dir and private_dir) into a unified tree TSV:
# <key_hex>\t<size_bytes>\t<sha256>\t<area>
manifest_scan_media_roots() {
  uploads_dir=$1
  private_dir=$2
  out_tsv=$3
  tmp_pub=${out_tsv}.pub
  tmp_leg=${out_tsv}.leg
  tmp_priv=${out_tsv}.priv

  manifest_scan_tree "$uploads_dir/public" "public/" "$tmp_pub"
  : > "$tmp_leg"
  if [ -d "$uploads_dir" ]; then
    find "$uploads_dir" -mindepth 1 -maxdepth 1 ! -name 'public' ! -name '.media-*.tmp' | while IFS= read -r entry; do
      name=${entry##*/}
      if [ -d "$entry" ]; then
        manifest_scan_tree "$entry" "$name/" "${tmp_leg}.part"
        cat "${tmp_leg}.part" >> "$tmp_leg"
        rm -f "${tmp_leg}.part"
      elif [ -f "$entry" ]; then
        size=$(stat -c "%s" "$entry")
        sum=$(sha256sum "$entry" | awk '{print $1}')
        hex=$(manifest_hex_encode "$name")
        printf '%s\t%s\t%s\n' "$hex" "$size" "$sum" >> "$tmp_leg"
      fi
    done
  fi
  manifest_scan_tree "$private_dir" "private/" "$tmp_priv"

  {
    awk 'BEGIN{OFS="\t"} {print $1, $2, $3, "public"}' "$tmp_pub"
    awk 'BEGIN{OFS="\t"} {print $1, $2, $3, "uploads_other"}' "$tmp_leg"
    awk 'BEGIN{OFS="\t"} {print $1, $2, $3, "private"}' "$tmp_priv"
  } > "$out_tsv"
  rm -f "$tmp_pub" "$tmp_leg" "$tmp_priv"
}

# Build media-manifest.tsv and enforce completeness/size consistency against:
#   - db_media_tsv (id|visibility|category|size_bytes|key_hex) from PG snapshot
#   - observed_public_tsv (key_hex\tsize\tsha256) from pre-tar public scan (optional)
#   - scanned_tree_tsv (key_hex\tsize\tsha256\tarea) from archived/staged files
manifest_build_and_verify() {
  scanned_tsv=$1
  db_tsv=$2
  observed_pub_tsv=$3
  stamp=$4
  snap_id=$5
  backend=$6
  out_manifest=$7

  awk -v stamp="$stamp" -v snap="$snap_id" -v backend="$backend" '
    BEGIN {
      FS = "\t"
      err = 0
    }
    FILENAME == ARGV[1] {
      # scanned_tsv: key_hex, size, sha256, area
      if (NF >= 4) {
        f_size[$1] = $2
        f_sha[$1] = $3
        f_area[$1] = $4
        f_order[++f_count] = $1
      }
      next
    }
    FILENAME == ARGV[2] {
      # observed_pub_tsv: key_hex, size, sha256
      if (NF >= 2) {
        obs_key = $1
        obs_size = $2
        obs_sha = (NF >= 3 ? $3 : "")
        if (!(obs_key in f_size)) {
          printf "inconsistent: public file vanished during archiving (key_hex=%s)\n", obs_key > "/dev/stderr"
          err = 1
        } else if (f_size[obs_key] != obs_size) {
          printf "inconsistent: public file size changed during archiving (key_hex=%s before=%s after=%s)\n", obs_key, obs_size, f_size[obs_key] > "/dev/stderr"
          err = 1
        } else if (obs_sha != "" && f_sha[obs_key] != obs_sha) {
          printf "inconsistent: public file checksum changed during archiving (key_hex=%s)\n", obs_key > "/dev/stderr"
          err = 1
        }
      }
      next
    }
    FILENAME == ARGV[3] {
      # db_tsv: pipe-delimited id|visibility|category|size_bytes|key_hex
      split($0, d, "|")
      if (length(d[1]) == 0) next
      id = d[1]; vis = d[2]; cat = d[3]; db_sz = d[4]; khex = d[5]
      if (!(khex in f_size)) {
        printf "inconsistent: missing file for media id=%s visibility=%s key_hex=%s\n", id, vis, khex > "/dev/stderr"
        err = 1
        next
      }
      if (db_sz + 0 >= 0 && f_size[khex] != db_sz) {
        printf "inconsistent: size mismatch for media id=%s expected=%s actual=%s key_hex=%s\n", id, db_sz, f_size[khex], khex > "/dev/stderr"
        err = 1
        next
      }
      seen[khex] = 1
      lines[++line_n] = sprintf("db\t%s\t%s\t%s\t%s\t%s\t%s", id, vis, cat, f_size[khex], f_sha[khex], khex)
      next
    }
    END {
      if (err) exit 1
      print "# meetstudent-media-manifest v1"
      print "# timestamp=" stamp
      print "# snapshot_id=" snap
      print "# storage_backend=" backend
      print "# columns: source\tmedia_id\tvisibility\tcategory\tsize_bytes\tsha256\tstorage_key_hex"
      for (i = 1; i <= line_n; i++) {
        print lines[i]
      }
      for (i = 1; i <= f_count; i++) {
        k = f_order[i]
        if (seen[k]) continue
        if (f_area[k] == "public") {
          printf "legacy_public\t-\tPUBLIC\tLEGACY_PUBLIC\t%s\t%s\t%s\n", f_size[k], f_sha[k], k
        } else if (f_area[k] == "private") {
          printf "unreferenced_private\t-\tPRIVATE\tORPHAN\t%s\t%s\t%s\n", f_size[k], f_sha[k], k
        } else {
          printf "unreferenced_uploads\t-\tUNKNOWN\tORPHAN\t%s\t%s\t%s\n", f_size[k], f_sha[k], k
        }
      }
    }
  ' "$scanned_tsv" "$observed_pub_tsv" "$db_tsv" > "$out_manifest"
}

# Verify a restored/unpacked media tree against media-manifest.tsv.
manifest_verify_tree() {
  manifest_file=$1
  scanned_tsv=$2
  awk '
    BEGIN { FS = "\t"; err = 0 }
    FILENAME == ARGV[1] {
      if (NF >= 3) {
        f_size[$1] = $2
        f_sha[$1] = $3
      }
      next
    }
    FILENAME == ARGV[2] {
      if ($0 ~ /^#/ || NF == 0) next
      # source, media_id, visibility, category, size_bytes, sha256, key_hex
      src = $1; sz = $5; sha = $6; khex = $7
      if (!(khex in f_size)) {
        printf "restore: missing media file in archive (source=%s key_hex=%s)\n", src, khex > "/dev/stderr"
        err = 1
      } else if (f_size[khex] != sz) {
        printf "restore: size mismatch for media (key_hex=%s expected=%s actual=%s)\n", khex, sz, f_size[khex] > "/dev/stderr"
        err = 1
      } else if (f_sha[khex] != sha) {
        printf "restore: sha256 mismatch for media (key_hex=%s)\n", khex > "/dev/stderr"
        err = 1
      }
      next
    }
    END { if (err) exit 1 }
  ' "$scanned_tsv" "$manifest_file"
}
