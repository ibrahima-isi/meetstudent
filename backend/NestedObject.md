Pour définir automatiquement l'utilisateur connecté comme créateur ou modificateur de l'entité School, vous pouvez utiliser plusieurs approches. Je vous propose une solution qui utilise Spring Security et un intercepteur pour récupérer l'utilisateur connecté.

Voici les étapes à suivre :

1. Créez une classe pour gérer l'authentification et récupérer l'utilisateur courant :

```java
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationFacade {

    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public UserEntity getCurrentUser() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserEntity) {
            return (UserEntity) authentication.getPrincipal();
        }
        return null;
    }

    public Integer getCurrentUserId() {
        UserEntity currentUser = getCurrentUser();
        return currentUser != null ? currentUser.getId() : null;
    }
}
```

2. Modifiez la classe BaseEntity pour ajouter un attribut d'écoute qui utilise cette facade :

```java
import org.springframework.beans.factory.annotation.Autowired;

@MappedSuperclass
public abstract class BaseEntity {
    // ... autres attributs existants

    @Transient
    @Autowired
    private AuthenticationFacade authenticationFacade;

    @PrePersist
    protected void onCreate() {
        setCreatedAt(Utils.dakarTimeZone());
        setModifiedAt(Utils.dakarTimeZone());
        
        if (authenticationFacade != null) {
            Integer currentUserId = authenticationFacade.getCurrentUserId();
            if (currentUserId != null) {
                setCreatedBy(currentUserId);
                setModifiedBy(currentUserId);
            }
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.setModifiedAt(Utils.dakarTimeZone());
        
        if (authenticationFacade != null) {
            Integer currentUserId = authenticationFacade.getCurrentUserId();
            if (currentUserId != null) {
                setModifiedBy(currentUserId);
            }
        }
    }
}
```

3. Configuration Spring Security :
   Assurez-vous que votre configuration Spring Security est paramétrée pour charger l'utilisateur complet comme principal dans le contexte de sécurité. Typiquement, cela se fait dans un service d'authentification personnalisé.

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        return new org.springframework.security.core.userdetails.User(
            user.getEmail(), 
            user.getPassword(), 
            // Convertissez les rôles en GrantedAuthority si nécessaire
            Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}
```

Avantages de cette approche :
- Automatique : Pas besoin de définir manuellement le créateur/modificateur
- Flexible : Fonctionne pour toutes les entités héritant de BaseEntity
- Sécurisé : Utilise le contexte de sécurité de Spring

Points importants à noter :
- Cette méthode nécessite que l'utilisateur soit authentifié
- Utilisez l'annotation `@Autowired` pour l'injection de dépendance
- Assurez-vous que Spring Security est correctement configuré dans votre application

Exemple de configuration Spring Security :
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
            );
        return http.build();
    }
}
```

Cette approche vous permettra de définir automatiquement l'utilisateur connecté comme créateur ou modificateur de l'entité School et des autres entités héritant de BaseEntity.