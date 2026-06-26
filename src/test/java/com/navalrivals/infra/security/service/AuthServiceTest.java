package com.navalrivals.infra.security.service;

import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Deve retornar usuário quando encontrado")
    void shouldReturnUserWhenEmailExists(){
        String emailTeste = "testandodasilva@email";
        User user = new User(UUID.randomUUID(), "Teste da Silva", emailTeste , "123");

        when(userRepository.findByEmail(emailTeste)).thenReturn(Optional.of(user));

        UserDetails result = authService.loadUserByUsername(emailTeste);

        assertNotNull(result);
        assertEquals(emailTeste, result.getUsername());
    }

    @Test
    @DisplayName("Deve lançar exception quando usuário não for encontrado")
    void shouldThrowAnExceptionWhenTheUserIsNotFound(){

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            authService.loadUserByUsername("testandosilva@email.com");
        });

    }
}