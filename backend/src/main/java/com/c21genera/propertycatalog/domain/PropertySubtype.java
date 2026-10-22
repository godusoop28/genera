package com.c21genera.propertycatalog.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Subcategoría seleccionable (p. ej. "Casa en condominio"). Nunca se borra: si
 * se desactiva deja de ofrecerse en capturas nuevas, pero los registros que ya
 * la tienen la siguen mostrando.
 *
 * <ul>
 *   <li>caseType: tratamiento de documentos y contrato (ver expedientes.PropertyCaseType).
 *   <li>online21Type / online21Subtype: cómo se publica en 21Online.
 *   <li>requiresDetail: "Otro — especificar" pide describir el inmueble.
 *   <li>keyCode: letras del tipo en la clave estructurada del inmueble (Control de Cambios #27),
 *       p. ej. "C" de Casa en CVEA03. null = no se genera clave automática.
 * </ul>
 */
@Entity
@Table(name = "property_subtype")
public class PropertySubtype extends AuditableEntity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID categoryId;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false)
  private boolean requiresDetail;

  @Column(nullable = false, length = 24)
  private String caseType;

  @Column(nullable = false, length = 24)
  private String online21Type;

  @Column(nullable = false, length = 40)
  private String online21Subtype;

  @Column(length = 3)
  private String keyCode;

  protected PropertySubtype() {}

  public PropertySubtype(
      UUID categoryId, String name, int sortOrder, boolean requiresDetail, String caseType, String online21Type, String online21Subtype, String keyCode) {
    this.id = UUID.randomUUID();
    this.categoryId = categoryId;
    this.active = true;
    update(categoryId, name, sortOrder, true, requiresDetail, caseType, online21Type, online21Subtype, keyCode);
  }

  public void update(
      UUID categoryId,
      String name,
      int sortOrder,
      boolean active,
      boolean requiresDetail,
      String caseType,
      String online21Type,
      String online21Subtype,
      String keyCode) {
    this.categoryId = categoryId;
    this.name = name;
    this.sortOrder = sortOrder;
    this.active = active;
    this.requiresDetail = requiresDetail;
    this.caseType = caseType;
    this.online21Type = online21Type;
    this.online21Subtype = online21Subtype;
    this.keyCode = keyCode;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getName() {
    return name;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isRequiresDetail() {
    return requiresDetail;
  }

  public String getCaseType() {
    return caseType;
  }

  public String getOnline21Type() {
    return online21Type;
  }

  public String getOnline21Subtype() {
    return online21Subtype;
  }

  public String getKeyCode() {
    return keyCode;
  }
}
