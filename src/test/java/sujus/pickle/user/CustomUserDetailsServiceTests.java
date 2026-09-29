package sujus.pickle.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTests {
    @Test
    void oidcOnlyAccountsCannotLogInWithALocalPassword() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmailIgnoreCase("oidc@example.test")).thenReturn(Optional.of(
                new AppUser("OIDC", "User", "oidc@example.test", null, Role.CUSTOMER)));
        assertThatThrownBy(() -> new CustomUserDetailsService(users).loadUserByUsername("oidc@example.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
