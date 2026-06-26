package com.navalrivals.domain.user.service;

import com.navalrivals.domain.user.dto.LoginUserRequest;
import com.navalrivals.domain.user.dto.RegisterUserRequest;
import com.navalrivals.domain.user.dto.UserResponse;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.BadCredencialsException;
import com.navalrivals.infra.exception.exceptions.UserAlreadyExistsException;
import com.navalrivals.infra.security.dto.AuthResponse;
import com.navalrivals.infra.security.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterUserRequest data){

        if (userRepository.existsByEmail(data.email())){
            throw new UserAlreadyExistsException("Usuário já cadastrado");
        }

        var encryptedPassword = new BCryptPasswordEncoder().encode(data.password());

        var user = new User(data, encryptedPassword);

        userRepository.save(user);

        // CASO EU TROQUE DE IDEIA E QUEIRA IMPLEMENTAR EMAIL DE BOAS VINDAS DEVO DE COLOCAR BEM NESSA PARTE AQ

        var authenticationToken = new UsernamePasswordAuthenticationToken(data.email(), data.password());
        var authentication = authenticationManager.authenticate(authenticationToken);
        var tokenJwt = tokenService.generateToken((User) authentication.getPrincipal());

        return new AuthResponse(tokenJwt, new UserResponse(user));
    }

    public AuthResponse login(LoginUserRequest data){
        try {
            var authenticationToken = new UsernamePasswordAuthenticationToken(data.email(), data.password());
            var authentication = authenticationManager.authenticate(authenticationToken);

            var user = (User) authentication.getPrincipal();
            var tokenJwt = tokenService.generateToken(user);

            return new AuthResponse(tokenJwt, new UserResponse(user));

        } catch (Exception e){
            throw new BadCredencialsException("E-mail ou senha incorretos");
        }
    }

}
