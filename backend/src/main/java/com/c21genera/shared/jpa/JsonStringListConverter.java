package com.c21genera.shared.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;

/** Lista de textos guardada como arreglo JSON en una columna TEXT (vacía = NULL). */
@Converter
public class JsonStringListConverter implements AttributeConverter<List<String>, String> {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<String> value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return JSON.writeValueAsString(value);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("No se pudo guardar la lista como JSON", e);
    }
  }

  @Override
  public List<String> convertToEntityAttribute(String column) {
    if (column == null || column.isBlank()) {
      return new ArrayList<>();
    }
    try {
      return new ArrayList<>(JSON.readValue(column, new TypeReference<List<String>>() {}));
    } catch (java.io.IOException e) {
      throw new IllegalStateException("JSON ilegible en la base", e);
    }
  }
}
