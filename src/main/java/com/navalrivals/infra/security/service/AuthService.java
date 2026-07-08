package com.navalrivals.infra.security.service;

import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        if (login.contains("@")) {
            return userRepository.findByEmail(login)
                    .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        }
        return userRepository.findByNickname(login)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
    }
}
