package com.estudos.users_api.exception

import java.util.UUID

class UserNotFoundException(id: String)
    : RuntimeException("user with id '$id' not found")
