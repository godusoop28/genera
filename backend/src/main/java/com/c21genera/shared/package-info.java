/**
 * Kernel compartido: utilidades, configuración base, manejo de errores y
 * abstracciones (storage, jobs) que cualquier módulo puede usar. Marcado
 * como módulo Modulith abierto: no está sujeto a las restricciones de
 * visibilidad que aplican entre los módulos de dominio.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.c21genera.shared;
