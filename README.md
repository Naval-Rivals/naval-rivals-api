# ⚓ Naval Rivals — API Backend

API RESTful e WebSocket do jogo **Naval Rivals**, uma releitura moderna do clássico Batalha Naval com modo tático, sistema de habilidades especiais e ranking competitivo.

---

## Sumário

- [Stack Tecnológica](#-stack-tecnológica)
- [Arquitetura](#-arquitetura)
- [Justificativas Técnicas](#-justificativas-técnicas)
- [Estrutura de Diretórios](#-estrutura-de-diretórios)
- [Funcionalidades](#-funcionalidades)
- [Como Executar](#-como-executar)
- [Variáveis de Ambiente](#-variáveis-de-ambiente)
- [Documentação da API](#-documentação-da-api)

---

## Stack Tecnológica

| Camada                    | Tecnologia                       | Versão   |
| ------------------------- | -------------------------------- | -------- |
| **Linguagem**             | Java                             | 21       |
| **Framework**             | Spring Boot                      | 4.0.7    |
| **Persistência**          | Spring Data JPA (Hibernate)      | —        |
| **Banco de Dados**        | PostgreSQL                       | —        |
| **Migrations**            | Flyway                           | —        |
| **Segurança**             | Spring Security + JWT (auth0)    | 4.5.1    |
| **Comunicação Real-time** | WebSocket STOMP (Spring)         | —        |
| **Validação**             | Bean Validation (Jakarta)        | —        |
| **Documentação API**      | Springdoc OpenAPI (Swagger UI)   | 3.0.2    |
| **Boilerplate**           | Lombok                           | —        |
| **Build**                 | Maven                            | —        |
| **Container**             | Docker (Eclipse Temurin 21)      | —        |

---

## Arquitetura

A aplicação segue uma **arquitetura Domain-Driven** com separação clara entre domínio e infraestrutura:

```
┌─────────────────────────────────────────────────────────────┐
│                   NavalrivalsApplication                    │
│               Spring Boot (Stateless / JWT)                 │
├─────────────────────────────────────────────────────────────┤
│                       Config                                │
│       WebSocket (STOMP) │ CORS │ Async                      │
├──────────────┬──────────────────────────────────────────────┤
│   Domain     │  Módulos de negócio autocontidos             │
│              │  user │ room │ game │ ranking │ stats        │
│              │  ship │ board │ shot │ position              │
│              │  (controller → service → repository/entity)  │
├──────────────┼──────────────────────────────────────────────┤
│   Infra      │  Segurança (Filter, Token, WS Interceptor)   │
│              │  Exceções (GlobalExceptionHandler)           │
├──────────────┼──────────────────────────────────────────────┤
│   Database   │  PostgreSQL + Flyway Migrations              │
│              │  users │ stats │ rooms │ game_results        │
└──────────────┴──────────────────────────────────────────────┘
```

### Fluxo de Comunicação

```
Cliente ──── REST (HTTP) ────► Controllers ────► Services ────► Repositories
   │                                                │
   └──── WebSocket (STOMP) ──► WS Controllers ──────┘
              /ws endpoint
         /topic/* (broadcast)
         /queue/* (privado)
         /app/* (client → server)
```

---

## Justificativas Técnicas

### Java 21 + Spring Boot 4
Versão LTS mais recente do Java, com Spring Boot garantindo produtividade e um ecossistema maduro para APIs REST e WebSocket no mesmo projeto.

### WebSocket STOMP
O jogo depende de comunicação em tempo real — turnos, ataques e status de sala precisam refletir instantaneamente para os dois jogadores. STOMP se integra nativamente ao Spring, sem exigir infraestrutura extra.

### JWT Stateless
Autenticação sem sessão no servidor, o que facilita escalar horizontalmente. O mesmo token valida tanto requisições REST quanto conexões WebSocket.

### Flyway
Controle de versão do banco de dados, garantindo que o schema evolua de forma consistente em todos os ambientes.

### Arquitetura por domínio
Cada área do jogo (usuário, sala, partida, ranking...) é organizada como um módulo independente, facilitando manutenção e testes isolados.

### Estado da partida em memória
Enquanto o jogo está em andamento, tabuleiros e tiros ficam em memória para garantir baixa latência nos turnos — só o resultado final é salvo no banco.

---

## Estrutura de Diretórios

```
src/main/java/com/navalrivals/
├── config/          # WebSocket, CORS, Async
├── domain/          # user, room, game, ranking, stats, ship, board, shot, position
│                     (cada um: controller → service → repository/entity/dto)
├── infra/
│   ├── security/    # JWT filter, WS interceptor, TokenService
│   └── exception/   # GlobalExceptionHandler
└── NavalrivalsApplication.java
```

---

## Funcionalidades

### Modos de Jogo

| Modo        | Descrição                                                    |
| ----------- | ------------------------------------------------------------ |
| **CLASSIC** | Batalha Naval tradicional — turnos alternados com ataques    |
| **TACTICAL**| Adiciona habilidades especiais que mudam a dinâmica do jogo  |

### Habilidades Especiais (Modo Tático)

| Habilidade   | Efeito                                              |
| ------------ | --------------------------------------------------- |
| **TORPEDO**  | Ataque em linha que percorre a coluna/linha inteira  |
| **RADAR**    | Revela uma área do tabuleiro inimigo                 |
| **SHIELD**   | Protege uma área do tabuleiro por um turno           |
| **EMP_NAVAL**| Desabilita habilidades do oponente temporariamente   |

### Fluxo da Partida

1. **Registro/Login** — autenticação JWT com endpoints REST
2. **Criar/Entrar Sala** — via código de sala (REST + WebSocket)
3. **Aguardando Oponente** — notificação em tempo real via STOMP
4. **Posicionamento de Navios** — validação server-side de regras
5. **Batalha** — turnos alternados com timer (60s), ataques e habilidades
6. **Resultado** — persistência de estatísticas e atualização de ranking

### Mecânicas do Servidor

- **Turn Timer** — timeout de 60s por turno com penalidade automática
- **Reconexão** — janela de 30s para reconectar sem perder a partida
- **Cleanup Schedulers** — limpeza automática de jogos abandonados (15min), em progresso inativos (10min) e finalizados (2min)
- **Disconnect Listener** — detecta desconexão WebSocket e inicia timer de reconexão

### Outros Recursos

- Ranking competitivo (top jogadores por vitórias)
- Estatísticas detalhadas por jogador (total de jogos, vitórias, derrotas)
- Histórico de partidas com métricas (tiros, acertos, navios destruídos, duração)
- Validação completa de posicionamento de navios
- Exception handling global com respostas padronizadas
- Documentação Swagger UI automática

---

## Como Executar

### Pré-requisitos

- Java 21+
- Maven 3.9+
- PostgreSQL 15+
- Docker (opcional)

### Execução Local

```bash
# Clonar o repositório
git clone https://github.com/Naval-Rivals/naval-rivals-api.git
cd navalrivals-api

# Configurar variáveis de ambiente (copiar e ajustar)
cp .env.example .env

# Executar com Maven
./mvnw spring-boot:run
```

### Execução com Docker

```bash
# Build da imagem
docker build -t navalrivals-api .

# Executar o container
docker run -p 8080:8080 --env-file .env navalrivals-api
```

A API estará disponível em `http://localhost:8080`.

---

## Variáveis de Ambiente

Crie um arquivo `.env` na raiz do projeto (ou configure no ambiente):

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/naval_rivals_db?useSSL=false&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SECRET_TOKEN_API=secret-token
FRONT_DOMAIN=http://localhost:5173
SPRING_PROFILES_ACTIVE=dev
```

| Variável                     | Descrição                                          | Exemplo                                   |
| ---------------------------- | -------------------------------------------------- | ----------------------------------------- |
| `SPRING_DATASOURCE_URL`     | URL de conexão JDBC com o PostgreSQL               | `jdbc:postgresql://localhost:5432/naval_rivals_db` |
| `SPRING_DATASOURCE_USERNAME`| Usuário do banco de dados                          | `postgres`                                |
| `SPRING_DATASOURCE_PASSWORD`| Senha do banco de dados                            | `postgres`                                |
| `SECRET_TOKEN_API`          | Secret para assinatura dos tokens JWT              | `minha-chave-secreta`                     |
| `FRONT_DOMAIN`              | Origem permitida pelo CORS (frontend)              | `http://localhost:5173`                   |
| `SPRING_PROFILES_ACTIVE`    | Profile ativo do Spring                            | `dev`                                     |

---

## Documentação da API

A documentação completa de todos os endpoints (REST e WebSocket) está disponível em:

📄 **[API_DOCUMENTATION.md](./API_DOCUMENTATION.md)**

### Swagger UI (em execução)

Com a aplicação rodando, acesse a documentação interativa:

```
http://localhost:8080/swagger-ui.html
```
