# Autocompletado del navegador y del teclado (Controles de Cambios #41 y #47)

## CC#41 — Sugerencias de contactos al crear un usuario

**Qué pasaba.** En «Crear usuario» los campos *Nombre completo* y *Correo* tenían
`autocomplete="off"`, pero Chrome (y Edge, que usa el mismo motor) **ignora ese valor**
en campos que reconoce como nombre/correo y ofrece las direcciones y contactos guardados
en el navegador — normalmente los datos del propio administrador. No es una lista que
genere el sistema: Genera no consulta contactos de nadie.

**Qué se cambió (web).** Los campos usan ahora un `name` y un token de `autocomplete`
propios (`genera-new-user-email`, …) que el navegador no asocia con ningún dato guardado,
sin autocorrección ni mayúsculas automáticas en el correo, y con los atributos que
desactivan los gestores de contraseñas más comunes (`data-1p-ignore`, `data-lpignore`,
`data-bwignore`). Lo mismo en «Editar usuario». El correo sigue validándose igual y el
flujo de alta + enlace no cambió.

**Qué se cambió (app móvil).** Los campos del alta/edición de usuario no tienen
autocompletado y ahora tampoco sugerencias ni autocorrección del teclado.

**Limitaciones (fuera del alcance de la página).**

- El teclado del celular (Gboard, teclado de iOS/Samsung) puede mostrar contactos o
  correos en su barra de sugerencias. Eso lo decide el teclado, no el sitio. Si molesta:
  Gboard → Configuración → Corrección ortográfica → desactivar «Sugerir contactos»;
  iOS → Ajustes → General → Teclado → desactivar «Predictivo» (afecta a todas las apps).
- Si el navegador sigue proponiendo algo, son sugerencias **que no se aplican solas**:
  solo se rellenan si se elige una. Se pueden borrar desde el desplegable (Mayús+Supr en
  Chrome) o en `chrome://settings/addresses`.
- Falta probarlo en el navegador/dispositivo donde Jorge lo reportó (no se indicó cuál).

## CC#47 — La anotación se sobrescribía al autocompletar la contraseña

**Por qué pasaba.** Los gestores de contraseñas (el de Chrome, 1Password, etc.) buscan el
campo de *usuario* como el último campo de texto antes del de contraseña. En «Autorizar
inconsistencia» ese campo era la anotación, así que al elegir la contraseña guardada
escribían el correo encima del texto.

**Qué se cambió.** El cuadro incluye un campo oculto de solo lectura con el correo de la
sesión (`autocomplete="username"`), que es el que el gestor reconoce ahora como usuario.
La anotación lleva `autocomplete="off"` y los atributos que la excluyen de los gestores.
La contraseña sigue con `autocomplete="current-password"`, así que el gestor la sigue
llenando. Si la contraseña es incorrecta se borra solo la contraseña: la anotación se
conserva y la inconsistencia no queda autorizada. La contraseña no se guarda en la
anotación ni en la bitácora (sin cambios en el backend).

**Pendiente.** Probar con el navegador y gestor de contraseñas de Jorge.
