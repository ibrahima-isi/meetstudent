# Production images of MeetStudent, built by the CI and published on GHCR
# (ADR 0003, coolify-deploy/01). The VPS never builds: Coolify pulls these.
#
# One entry per image in the matrix below; the CI builds them all on a pull
# request and pushes them all on `main`. Run it from the repository root, as
# the CI does (`docker buildx bake -f infra/docker-bake.hcl`): build contexts
# are resolved from the working directory, not from this file's folder.
# Tests: infra/tests/ci-images.test.sh.

variable "REGISTRY" {
  default = "ghcr.io/ibrahima-isi"
}

# The commit the images are built from. GitHub Actions sets it in the
# environment, and bake reads variables from the environment. The rollback
# (IMAGE_TAG=sha-<commit> in Coolify) needs the full 40-character SHA.
variable "GITHUB_SHA" {
  default = ""
  validation {
    condition     = can(regex("^[0-9a-f]{40}$", GITHUB_SHA))
    error_message = "GITHUB_SHA must be the full 40-character commit SHA the images are built from."
  }
}

group "default" {
  targets = ["images"]
}

target "images" {
  name = image.name
  matrix = {
    image = [
      { name = "meetstudent-api", context = "apps/api", args = {} },
      { name = "meetstudent-web", context = "apps/web", args = {} },
      { name = "meetstudent-backoffice", context = "apps/backoffice", args = { DIST_DIR = "dist/backoffice/browser" } },
    ]
  }
  context   = image.context
  args      = image.args
  platforms = ["linux/amd64"]
  tags = [
    "${REGISTRY}/${image.name}:sha-${GITHUB_SHA}",
    "${REGISTRY}/${image.name}:latest",
  ]
}
