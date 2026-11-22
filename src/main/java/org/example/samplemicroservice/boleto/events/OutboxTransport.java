package org.example.samplemicroservice.boleto.events;

interface OutboxTransport {
    void publish(BoletoEvent event, String traceparent) throws PublishFailure;

    final class PublishFailure extends Exception {}
}
