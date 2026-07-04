export interface RegisterRequest {
  name: string
  email: string
  cpf: string
  phone?: string
  password: string
}

export interface RegisterResponse {
  userId: string
  email: string
  name: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
}

export interface AuthUser {
  userId: string
  email: string
  name: string
}
