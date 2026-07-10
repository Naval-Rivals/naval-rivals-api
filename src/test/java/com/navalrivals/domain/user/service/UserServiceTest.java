package com.navalrivals.domain.user.service;

import com.navalrivals.domain.user.dto.LoginUserRequest;
import com.navalrivals.domain.user.dto.RegisterUserRequest;
import com.navalrivals.domain.user.dto.UpdateNicknameRequest;
import com.navalrivals.domain.user.dto.UpdatePasswordRequest;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.BadCredencialsException;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.exception.exceptions.PasswordNotConfirmationException;
import com.navalrivals.infra.exception.exceptions.UserAlreadyExistsException;
import com.navalrivals.infra.security.dto.AuthResponse;
import com.navalrivals.infra.security.service.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private BCryptPasswordEncoder encoder;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("Deve lançar UserAlreadyExistsException quando email já existe")
        void shouldThrowWhenEmailAlreadyExists() {
            var request = new RegisterUserRequest("player1", "player@email.com", "123456", "123456");

            when(userRepository.existsByEmail(request.email())).thenReturn(true);

            assertThrows(UserAlreadyExistsException.class, () -> userService.register(request));

            verify(userRepository).existsByEmail(request.email());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar PasswordNotConfirmationException quando senhas não coincidem")
        void shouldThrowWhenPasswordsDoNotMatch() {
            var request = new RegisterUserRequest("player1", "player@email.com", "123456", "654321");

            when(userRepository.existsByEmail(request.email())).thenReturn(false);

            assertThrows(PasswordNotConfirmationException.class, () -> userService.register(request));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar UserAlreadyExistsException quando nickname já existe")
        void shouldThrowWhenNicknameAlreadyExists() {
            var request = new RegisterUserRequest("player1", "player@email.com", "123456", "123456");

            when(userRepository.existsByEmail(request.email())).thenReturn(false);
            when(userRepository.existsByNickname(request.nickname())).thenReturn(true);

            assertThrows(UserAlreadyExistsException.class, () -> userService.register(request));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve registrar usuário com sucesso, salvar user + stats, autenticar e retornar token")
        void shouldRegisterSuccessfully() {
            var request = new RegisterUserRequest("player1", "player@email.com", "123456", "123456");
            var encryptedPassword = "encrypted_123456";
            var expectedToken = "jwt-token-123";

            when(userRepository.existsByEmail(request.email())).thenReturn(false);
            when(userRepository.existsByNickname(request.nickname())).thenReturn(false);
            when(encoder.encode(request.password())).thenReturn(encryptedPassword);

            Authentication authentication = mock(Authentication.class);
            User authenticatedUser = new User(request, encryptedPassword);
            authenticatedUser.setId(UUID.randomUUID());

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(authenticatedUser);
            when(tokenService.generateToken(authenticatedUser)).thenReturn(expectedToken);

            AuthResponse response = userService.register(request);

            assertNotNull(response);
            assertEquals(expectedToken, response.token());
            assertEquals(authenticatedUser.getNickname(), response.nickname());
            assertEquals(authenticatedUser.getEmail(), response.email());

            verify(userRepository).save(any(User.class));
            verify(encoder).encode(request.password());
            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(tokenService).generateToken(authenticatedUser);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("Deve realizar login com sucesso e retornar token")
        void shouldLoginSuccessfully() {
            var request = new LoginUserRequest("player@email.com", "123456");
            var expectedToken = "jwt-token-456";

            Authentication authentication = mock(Authentication.class);
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setNickname("player1");
            user.setEmail("player@email.com");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);
            when(tokenService.generateToken(user)).thenReturn(expectedToken);

            AuthResponse response = userService.login(request);

            assertNotNull(response);
            assertEquals(expectedToken, response.token());
            assertEquals(user.getNickname(), response.nickname());
            assertEquals(user.getEmail(), response.email());

            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(tokenService).generateToken(user);
        }

        @Test
        @DisplayName("Deve lançar BadCredencialsException quando credenciais são inválidas")
        void shouldThrowWhenCredentialsAreInvalid() {
            var request = new LoginUserRequest("player@email.com", "wrong_password");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new RuntimeException("Bad credentials"));

            assertThrows(BadCredencialsException.class, () -> userService.login(request));

            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(tokenService, never()).generateToken(any());
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("Deve retornar UserResponse quando usuário é encontrado")
        void shouldReturnUserWhenFound() {
            UUID userId = UUID.randomUUID();
            User user = new User();
            user.setId(userId);
            user.setNickname("player1");
            user.setEmail("player@email.com");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            var response = userService.getById(userId);

            assertNotNull(response);
            assertEquals(userId, response.id());
            assertEquals("player1", response.nickname());
            assertEquals("player@email.com", response.email());

            verify(userRepository).findById(userId);
        }

        @Test
        @DisplayName("Deve lançar NotFoundException quando usuário não é encontrado")
        void shouldThrowWhenUserNotFound() {
            UUID userId = UUID.randomUUID();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> userService.getById(userId));

            verify(userRepository).findById(userId);
        }
    }

    @Nested
    @DisplayName("changeNickname")
    class ChangeNickname {

        @Test
        @DisplayName("Deve alterar nickname com sucesso")
        void shouldChangeNicknameSuccessfully() {
            var request = new UpdateNicknameRequest("newNickname");
            UUID userId = UUID.randomUUID();

            User user = new User();
            user.setId(userId);
            user.setNickname("oldNickname");
            user.setEmail("player@email.com");

            User managedUser = new User();
            managedUser.setId(userId);
            managedUser.setNickname("oldNickname");
            managedUser.setEmail("player@email.com");

            when(userRepository.existsByNickname(request.nickname())).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(managedUser));

            var response = userService.changeNickname(request, user);

            assertNotNull(response);
            assertEquals("newNickname", response.nickname());

            verify(userRepository).existsByNickname(request.nickname());
            verify(userRepository).findById(userId);
        }

        @Test
        @DisplayName("Deve lançar UserAlreadyExistsException quando nickname já existe")
        void shouldThrowWhenNicknameAlreadyExists() {
            var request = new UpdateNicknameRequest("existingNickname");
            User user = new User();
            user.setId(UUID.randomUUID());

            when(userRepository.existsByNickname(request.nickname())).thenReturn(true);

            assertThrows(UserAlreadyExistsException.class, () -> userService.changeNickname(request, user));

            verify(userRepository).existsByNickname(request.nickname());
            verify(userRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("Deve alterar senha com sucesso")
        void shouldChangePasswordSuccessfully() {
            var request = new UpdatePasswordRequest("currentPass", "newPass123", "newPass123");

            User user = new User();
            user.setId(UUID.randomUUID());
            user.setPassword("encoded_current_pass");

            when(encoder.matches("currentPass", "encoded_current_pass")).thenReturn(true);
            when(encoder.matches("newPass123", "encoded_current_pass")).thenReturn(false);
            when(encoder.encode("newPass123")).thenReturn("encoded_new_pass");

            userService.changePassword(request, user);

            assertEquals("encoded_new_pass", user.getPassword());
            verify(userRepository).save(user);
            verify(encoder).encode("newPass123");
        }

        @Test
        @DisplayName("Deve lançar BadCredencialsException quando senha atual está incorreta")
        void shouldThrowWhenCurrentPasswordIsIncorrect() {
            var request = new UpdatePasswordRequest("wrongPass", "newPass123", "newPass123");

            User user = new User();
            user.setId(UUID.randomUUID());
            user.setPassword("encoded_current_pass");

            when(encoder.matches("wrongPass", "encoded_current_pass")).thenReturn(false);

            assertThrows(BadCredencialsException.class, () -> userService.changePassword(request, user));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar BadCredencialsException quando nova senha é igual à atual")
        void shouldThrowWhenNewPasswordEqualsCurrentPassword() {
            var request = new UpdatePasswordRequest("currentPass", "currentPass", "currentPass");

            User user = new User();
            user.setId(UUID.randomUUID());
            user.setPassword("encoded_current_pass");

            when(encoder.matches("currentPass", "encoded_current_pass")).thenReturn(true);
            when(encoder.matches("currentPass", "encoded_current_pass")).thenReturn(true);

            assertThrows(BadCredencialsException.class, () -> userService.changePassword(request, user));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar PasswordNotConfirmationException quando senhas não coincidem")
        void shouldThrowWhenPasswordConfirmationDoesNotMatch() {
            var request = new UpdatePasswordRequest("currentPass", "newPass123", "differentPass");

            User user = new User();
            user.setId(UUID.randomUUID());
            user.setPassword("encoded_current_pass");

            when(encoder.matches("currentPass", "encoded_current_pass")).thenReturn(true);
            when(encoder.matches("newPass123", "encoded_current_pass")).thenReturn(false);

            assertThrows(PasswordNotConfirmationException.class, () -> userService.changePassword(request, user));

            verify(userRepository, never()).save(any());
        }
    }
}
