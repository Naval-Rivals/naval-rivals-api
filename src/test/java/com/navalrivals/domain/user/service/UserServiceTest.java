package com.navalrivals.domain.user.service;

import com.navalrivals.domain.user.dto.*;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.BadCredencialsException;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.exception.exceptions.UserAlreadyExistsException;
import com.navalrivals.infra.security.dto.AuthResponse;
import com.navalrivals.infra.security.service.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    @DisplayName("Deve cadastrar corretamente o usuário")
    void shouldRegisterUserSuccessfully() {
        RegisterUserRequest request =
                new RegisterUserRequest("Teste", "email@email.com", "123");

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(encoder.encode(request.password())).thenReturn("hashed");

        User userMock = mock(User.class);
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userMock);
        when(tokenService.generateToken(userMock)).thenReturn("token");

        AuthResponse response = userService.register(request);

        assertNotNull(response);
        assertEquals("token", response.accessToken());

        verify(userRepository).save(any(User.class));
        verify(encoder).encode(request.password());
    }

    @Test
    @DisplayName("Deve lançar erro quando email já existe")
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        RegisterUserRequest request =
                new RegisterUserRequest("Teste", "email@email.com", "123");

        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class,
                () -> userService.register(request));
    }

    @Test
    @DisplayName("Deve logar corretamente")
    void shouldLoginSuccessfully() {
        LoginUserRequest request =
                new LoginUserRequest("email@email.com", "123");

        User user = mock(User.class);
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(tokenService.generateToken(user)).thenReturn("token");

        AuthResponse response = userService.login(request);

        assertNotNull(response);
        assertEquals("token", response.accessToken());
    }

    @Test
    @DisplayName("Deve lançar erro no login")
    void shouldThrowBadCredentialsOnLoginFailure() {
        LoginUserRequest request =
                new LoginUserRequest("email@email.com", "123");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new RuntimeException());

        assertThrows(BadCredencialsException.class,
                () -> userService.login(request));
    }

    @Test
    @DisplayName("Deve retornar usuário por ID")
    void shouldReturnUserById() {
        UUID id = UUID.randomUUID();
        User user = new User();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(id);

        assertNotNull(response);
    }

    @Test
    @DisplayName("Deve lançar erro quando usuário não encontrado")
    void shouldThrowWhenUserNotFound() {
        UUID id = UUID.randomUUID();

        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> userService.getById(id));
    }

    @Test
    @DisplayName("Deve alterar nickname com sucesso")
    void shouldChangeNickname() {
        UUID id = UUID.randomUUID();

        User user = mock(User.class);
        when(user.getId()).thenReturn(id);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UpdateNicknameRequest request =
                new UpdateNicknameRequest("novoNick");

        UserResponse response = userService.changeNickname(request, user);

        verify(user).setNickname("novoNick");
        assertNotNull(response);
    }

    @Test
    @DisplayName("Deve alterar senha com sucesso")
    void shouldChangePasswordSuccessfully() {
        User user = mock(User.class);

        UpdatePasswordRequest request =
                new UpdatePasswordRequest("old", "new");

        when(user.getPassword()).thenReturn("hashedOld");

        when(encoder.matches("old", "hashedOld")).thenReturn(true);
        when(encoder.matches("new", "hashedOld")).thenReturn(false);
        when(encoder.encode("new")).thenReturn("hashedNew");

        userService.changePassword(request, user);

        verify(user).setPassword("hashedNew");
    }

    @Test
    @DisplayName("Deve lançar erro se senha atual estiver incorreta")
    void shouldThrowWhenCurrentPasswordIsWrong() {
        User user = mock(User.class);

        when(user.getPassword()).thenReturn("hashed");

        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        UpdatePasswordRequest request =
                new UpdatePasswordRequest("wrong", "new");

        assertThrows(BadCredencialsException.class,
                () -> userService.changePassword(request, user));
    }

    @Test
    @DisplayName("Deve lançar erro se nova senha for igual")
    void shouldThrowWhenNewPasswordIsSame() {
        User user = mock(User.class);

        when(user.getPassword()).thenReturn("hashed");

        when(encoder.matches("old", "hashed")).thenReturn(true);
        when(encoder.matches("same", "hashed")).thenReturn(true);

        UpdatePasswordRequest request =
                new UpdatePasswordRequest("old", "same");

        assertThrows(BadCredencialsException.class,
                () -> userService.changePassword(request, user));
    }
}