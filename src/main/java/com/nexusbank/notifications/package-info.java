/**
 * Módulo Notifications — responsável por criar, persistir e servir notificações
 * in-app para os usuários. Consome eventos do Kafka publicados por Payments e
 * CoreBanking. Não expõe serviços chamáveis por outros módulos: toda comunicação
 * é orientada a eventos.
 *
 * API pública: somente NotificationsApi (interface vazia — módulo sem contrato
 * in-process para outros módulos).
 */
@org.springframework.modulith.ApplicationModule
package com.nexusbank.notifications;
