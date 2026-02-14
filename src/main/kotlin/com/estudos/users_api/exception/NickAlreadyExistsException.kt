package com.estudos.users_api.exception

class NickAlreadyExistsException(nick: String)
    : RuntimeException("nick '$nick' already exists")
