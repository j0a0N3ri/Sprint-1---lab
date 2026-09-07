package br.edu.iceibank.agencia.controller.dto;

/**
 * Corpo de POST /auth/login.
 *
 * Escolhi login e senha, e nao "numero da conta + senha", porque uma pessoa pode ser dona de
 * mais de uma conta - inclusive em agencias diferentes. Amarrar a identidade ao numero da
 * conta obrigaria a um login por conta e quebraria assim que alguem tivesse duas.
 */
public record LoginRequest(String login, String senha) {}
