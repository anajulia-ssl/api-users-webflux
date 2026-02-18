-- Tabela USERS
CREATE TABLE USERS (
                       ID          VARCHAR2(36)    NOT NULL,
                       NAME        VARCHAR2(255)   NOT NULL,
                       NICK        VARCHAR2(100),
                       BIRTH_DATE  DATE            NOT NULL,
                       CONSTRAINT PK_USERS PRIMARY KEY (ID)
);

-- Índices úteis (opcional)
CREATE INDEX IDX_USERS_NICK ON USERS (NICK);

-- Tabela STACKS
CREATE TABLE STACKS (
                        ID           VARCHAR2(36)  NOT NULL,
                        USER_ID      VARCHAR2(36)  NOT NULL,
                        NAME         VARCHAR2(100) NOT NULL,
                        SKILL_LEVEL  NUMBER(10)    NOT NULL,
                        CONSTRAINT PK_STACKS PRIMARY KEY (ID),
                        CONSTRAINT FK_STACKS_USERS
                            FOREIGN KEY (USER_ID)
                                REFERENCES USERS (ID)
                                    ON DELETE CASCADE
);

-- Índices úteis (opcional)
CREATE INDEX IDX_STACKS_USER_ID ON STACKS (USER_ID);
CREATE INDEX IDX_STACKS_NAME ON STACKS (NAME);