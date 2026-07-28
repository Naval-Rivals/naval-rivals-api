package com.navalrivals.domain.user.service;

import com.navalrivals.domain.stats.entity.Stats;
import com.navalrivals.domain.user.dto.*;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.BadCredencialsException;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.exception.exceptions.PasswordNotConfirmationException;
import com.navalrivals.infra.exception.exceptions.UserAlreadyExistsException;
import com.navalrivals.infra.security.dto.AuthResponse;
import com.navalrivals.infra.security.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final BCryptPasswordEncoder encoder;

    @Transactional
    public AuthResponse register(RegisterUserRequest data){

        if (userRepository.existsByEmail(data.email())){
            log.warn("[USER] Registro falhou — email já cadastrado: {}", data.email());
            throw new UserAlreadyExistsException("Usuário já cadastrado");
        }

        if (!data.password().equals(data.passwordConfirmation())){
            log.warn("[USER] Registro falhou — senhas não coincidem para email={}", data.email());
            throw new PasswordNotConfirmationException("As senhas não coincidem");
        }

        if (userRepository.existsByNickname(data.nickname())){
            log.warn("[USER] Registro falhou — nickname já em uso: {}", data.nickname());
            throw new UserAlreadyExistsException("Já existe um usuário com esse apelido");
        }

        var encryptedPassword = encoder.encode(data.password());

        var user = new User(data, encryptedPassword);

        var stats = new Stats(user);
        user.setStats(stats);

        userRepository.save(user);
        log.info("[USER] Novo usuário registrado — userId={}, nickname={}", user.getId(), user.getNickname());

        var authenticationToken = new UsernamePasswordAuthenticationToken(data.email(), data.password());
        var authentication = authenticationManager.authenticate(authenticationToken);
        var tokenJwt = tokenService.generateToken((User) authentication.getPrincipal());

        return new AuthResponse(tokenJwt, user);
    }

    public AuthResponse login(LoginUserRequest data){
        try {
            var authenticationToken = new UsernamePasswordAuthenticationToken(data.login(), data.password());
            var authentication = authenticationManager.authenticate(authenticationToken);

            var user = (User) authentication.getPrincipal();
            var tokenJwt = tokenService.generateToken(user);

            log.info("[USER] Login bem-sucedido — userId={}, nickname={}", user.getId(), user.getNickname());
            return new AuthResponse(tokenJwt, user);

        } catch (Exception e){
            log.warn("[USER] Login falhou — login={}", data.login());
            throw new BadCredencialsException("Credenciais inválidas");
        }
    }

    public UserResponse getById(UUID id){
        var user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        return new UserResponse(user);
    }

    @Transactional
    public UserResponse changeNickname(UpdateNicknameRequest data, User user){

        if (userRepository.existsByNickname(data.nickname())){
            log.warn("[USER] Alteração de nickname falhou — nickname já em uso: {}, userId={}", data.nickname(), user.getId());
            throw new UserAlreadyExistsException("Já existe um usuário com esse apelido");
        }

        var managedUser = userRepository.findById(user.getId())
                .orElseThrow();
        var oldNickname = managedUser.getNickname();
        managedUser.setNickname(data.nickname());
        log.info("[USER] Nickname alterado — userId={}, de '{}' para '{}'", user.getId(), oldNickname, data.nickname());
        return new UserResponse(managedUser);
    }

    @Transactional
    public void changePassword(UpdatePasswordRequest data, User user){

        if (!encoder.matches(data.currentPassword(), user.getPassword())){
            log.warn("[USER] Alteração de senha falhou — senha atual incorreta, userId={}", user.getId());
            throw new BadCredencialsException("Senha atual incorreta");
        }

        if (encoder.matches(data.newPassword(), user.getPassword())){
            log.warn("[USER] Alteração de senha falhou — nova senha igual à atual, userId={}", user.getId());
            throw new BadCredencialsException("Nova senha não pode ser igual a atual");
        }

        if (!data.newPassword().equals(data.passwordConfirmation())){
            log.warn("[USER] Alteração de senha falhou — senhas não coincidem, userId={}", user.getId());
            throw new PasswordNotConfirmationException("As senhas não coincidem");
        }

        var newEncryptedPassword = encoder.encode(data.newPassword());
        user.setPassword(newEncryptedPassword);

        userRepository.save(user);
        log.info("[USER] Senha alterada com sucesso — userId={}", user.getId());
    }

}
