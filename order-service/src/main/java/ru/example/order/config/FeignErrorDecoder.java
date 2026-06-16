package ru.example.order.config;

import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import java.nio.charset.StandardCharsets;

public class FeignErrorDecoder implements ErrorDecoder {

  @Override
  public Exception decode(String methodKey, Response response) {
    try {
      if (response.body() != null) {
        String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
        String message = extractMessage(body);
        return new IllegalArgumentException(message);
      }
    } catch (Exception e) {
      return new IllegalArgumentException("An error occurred during inter-service call");
    }
    return new Default().decode(methodKey, response);
  }

  private String extractMessage(String json) {
    if (json == null || json.isEmpty()) {
      return "An error occurred";
    }

    String message = json;

    if (json.contains("\"message\":\"")) {
      int start = json.indexOf("\"message\":\"") + 11;
      int end = json.lastIndexOf("\"");
      if (start > 10 && end > start) {
        message = json.substring(start, end);
      }
    }

    message = message.replace("400 BAD_REQUEST", "").replace("\"", "").replace("\\", "").trim();

    return message;
  }
}
