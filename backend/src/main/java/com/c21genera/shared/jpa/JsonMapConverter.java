package com.c21genera.shared.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;
import java.util.TreeMap;

/** Mapa de valores simples guardado como JSON en una columna TEXT (vacío = NULL). */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Map<String, Object> value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return JSON.writeValueAsString(new TreeMap<>(value));
    } catch (java.io.IOException e) {
      throw new IllegalStateException("No se pudo guardar el mapa como JSON", e);
    }
  }

  @Override
  public Map<String, Object> convertToEntityAttribute(String column) {
    if (column == null || column.isBlank()) {
      return new TreeMap<>();
    }
    try {
      return new TreeMap<>(JSON.readValue(column, new TypeReference<Map<String, Object>>() {}));
    } catch (java.io.IOException e) {
      throw new IllegalStateException("JSON ilegible en la base", e);
    }
  }
}
