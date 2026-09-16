// Referencia textual del contrato real registrado ante PROFECO:
// "Contrato de Prestación de Servicios de Intermediación Inmobiliaria con
// Exclusiva" (archivo fuente en la raíz del repositorio, sin datos).
//
// IMPORTANTE: este archivo transcribe fielmente apartados del documento
// fuente para fines de referencia y de vista previa del prototipo. No se
// reescriben cláusulas, no se "mejora" la redacción y no se eliminan
// apartados. Los campos entre [ ] son variables que en el documento real
// aparecen en blanco para llenarse caso por caso.
//
// TODO: confirmar dato canónico con CENTURY 21 Genera antes de producción
// (el domicilio de la intermediaria difiere ligeramente entre este contrato
// y el aviso de privacidad simplificado — no se normaliza aquí).

export const CONTRACT_TITLE =
  "Contrato de prestación de servicios de intermediación para la compraventa de inmueble destinado a casa habitación";

export const CONTRACT_PROFECO_NUMBER = "7/002193-2026";
export const CONTRACT_PROFECO_REGISTRATION_DATE = "19 de marzo de 2026";
export const CONTRACT_PROFECO_AUTHORITY =
  "Registro Público de Contratos de Adhesión de la Procuraduría Federal del Consumidor (PROFECO)";

export const CONTRACT_INTERMEDIARY_LEGAL_NAME = "Grupo WILGEN y Asociados S. de R.L. de C.V.";
export const CONTRACT_INTERMEDIARY_COMMERCIAL_NAME = "CENTURY 21 GENERA";
export const CONTRACT_LEGAL_REPRESENTATIVE = "Jorge Ricardo Jurado Espinal";
export const CONTRACT_INTERMEDIARY_ADDRESS =
  "Av Alta Tensión 403 local 1, Plaza Copa de Oro, Col. Cantarranas, C.P. 62448, Cuernavaca, Morelos";
export const CONTRACT_INTERMEDIARY_RFC = "GWA260224B11";
export const CONTRACT_INTERMEDIARY_EMAIL = "ventas@c21genera.com";
export const CONTRACT_INTERMEDIARY_PHONE = "777 454 0198";
export const CONTRACT_INTERMEDIARY_WHATSAPP = "777 800 5300";

export const CONTRACT_INCORPORATION_INSTRUMENT =
  "Instrumento público número 10,186, de fecha 24 de febrero de 2026, otorgado ante la fe de la Corredora Pública número 6 del Estado de Morelos, Lic. Norma Elvia Martel Mota, inscrito en el Registro Público de Comercio de Cuernavaca, Morelos, bajo el folio mercantil N-2026010454.";

export const CONTRACT_COMMISSION_RATE = 0.05;
export const CONTRACT_PENALTY_RATE = 1; // 100% de la comisión pactada
export const CONTRACT_EXCLUSIVITY_DAYS = 180;
export const CONTRACT_REVOCATION_BUSINESS_DAYS = 5;
export const CONTRACT_MODALITY = "EXCLUSIVA";

export const CONTRACT_DECLARATIONS = [
  {
    id: "declaracion-1",
    title: "I. Declara la intermediaria",
    text: 'Es una sociedad mercantil de Responsabilidad Limitada de Capital Variable, legalmente constituida conforme a las leyes de los Estados Unidos Mexicanos. Comparece a través de Jorge Ricardo Jurado Espinal en su carácter de representante legal. Cuenta con autorización comercial para operar bajo el nombre comercial CENTURY 21 GENERA. Su domicilio es el señalado y su RFC es GWA260224B11. Puso a disposición del cliente la información y documentación especificada en los Anexos B y C.',
  },
  {
    id: "declaracion-2",
    title: "II. Declara el cliente",
    text: "Declara su nacionalidad, identificación oficial, edad y estado civil (persona física) o datos de constitución (persona moral); su domicilio y RFC; que es legítimo propietario del inmueble, acreditado mediante escritura pública o contrato privado; el tipo de inmueble y, en su caso, si está sujeto a régimen de condominio; y la documentación e información con la que cuenta respecto del inmueble (vivienda o terreno destinado a casa habitación).",
  },
  {
    id: "declaracion-3",
    title: "III. Declaran las partes",
    text: "Es su voluntad celebrar el presente contrato.",
  },
] as const;

export const CONTRACT_CLAUSES = [
  {
    id: "primera",
    title: "Primera. Objeto",
    text: "La intermediaria se obliga a realizar asesoría, publicidad, intermediación, entrega de ofertas e información al cliente con el fin de conseguir un comprador del inmueble destinado a casa habitación referido en la declaración II, inciso d.",
  },
  {
    id: "segunda",
    title: "Segunda. Contraprestación económica",
    text: "Formalizada la compraventa, el cliente pagará a la intermediaria el 5% sobre el precio de compraventa efectivamente pagado, más el Impuesto al Valor Agregado correspondiente.",
  },
  {
    id: "tercera",
    title: "Tercera. Documentación",
    text: "El cliente entrega a la intermediaria, en copia simple, la documentación necesaria para el cumplimiento del contrato. Documentación adicional debe entregarse dentro de los 5 días hábiles siguientes al requerimiento.",
  },
  {
    id: "cuarta",
    title: "Cuarta. Derechos y obligaciones de las partes",
    text: "Enlista, de forma enunciativa mas no limitativa, los derechos y obligaciones de la intermediaria y del cliente (ver Anexo C — Carta de derechos del cliente).",
  },
  {
    id: "quinta",
    title: "Quinta. Vigencia",
    text: "180 días naturales. Vencido el plazo sin formalizarse la compraventa, la intermediaria debe devolver la documentación y no procede comisión.",
  },
  {
    id: "sexta",
    title: "Sexta. Exclusividad",
    text: "El contrato se celebra bajo modalidad de exclusividad respecto del inmueble. Si el cliente vende por intermediación de un tercero durante la vigencia, se obliga a pagar la pena convencional de la cláusula novena.",
  },
  {
    id: "septima",
    title: "Séptima. Revocación",
    text: "El cliente cuenta con 5 días hábiles posteriores a la firma para revocar su consentimiento sin responsabilidad, mediante aviso por escrito. La intermediaria reintegra cantidades dentro de los 15 días hábiles siguientes a la notificación.",
  },
  {
    id: "octava",
    title: "Octava. Rescisión",
    text: "Procede por incumplimiento de las obligaciones pactadas. Si el incumplimiento es de la intermediaria, además de la pena convencional debe restituir las cantidades pagadas dentro de los 15 días hábiles siguientes.",
  },
  {
    id: "novena",
    title: "Novena. Penas convencionales",
    text: "Pena convencional equivalente al 100% de la comisión pactada en la cláusula segunda, para el caso de incumplimiento de cualquiera de las obligaciones contraídas.",
  },
  {
    id: "decima",
    title: "Décima. Fallecimiento del cliente",
    text: "Los sucesores legítimos suceden en derechos y obligaciones, salvo que manifiesten su deseo de no continuar, caso en el cual la intermediaria restituye cantidades y documentación.",
  },
  {
    id: "decima-primera",
    title: "Décima primera. Servicios adicionales",
    text: "Listados en el Anexo D. Requieren consentimiento escrito previo del cliente para prestarse y para cobrarse.",
  },
  {
    id: "decima-segunda",
    title: "Décima segunda. Notificaciones entre las partes",
    text: "Deben hacerse por escrito, con acuse de recibo, en los domicilios/medios de contacto señalados por cada parte.",
  },
  {
    id: "decima-tercera",
    title: "Décima tercera. Canales de atención",
    text: "Atención presencial, WhatsApp 777 800 5300, teléfono 777 454 0198 y correo ventas@c21genera.com, de lunes a viernes 09:00–18:00 hrs y sábados 09:00–13:00 hrs. Plazo de respuesta: 72 hrs.",
  },
  {
    id: "decima-cuarta",
    title: "Décima cuarta. Datos personales",
    text: "El tratamiento de datos personales se rige por el Aviso de Privacidad anexo (Anexo E), conforme a la Ley Federal de Protección de Datos Personales en Posesión de los Particulares.",
  },
  {
    id: "decima-quinta",
    title: "Décima quinta. Competencia de PROFECO",
    text: "Ante controversias sobre interpretación o cumplimiento, el cliente puede acudir a la Procuraduría Federal del Consumidor.",
  },
  {
    id: "decima-sexta",
    title: "Décima sexta. Competencia jurisdiccional",
    text: "Las partes se someten a las autoridades jurisdiccionales del Primer Distrito Judicial del Estado de Morelos, con residencia en Cuernavaca, Morelos.",
  },
  {
    id: "decima-septima",
    title: "Décima séptima. Plazo de responsabilidad civil",
    text: "Diez años, contados a partir de la fecha en que la obligación pudo exigirse, conforme al Código Civil para el Estado Libre y Soberano de Morelos.",
  },
  {
    id: "decima-octava",
    title: "Décima octava. Registro del modelo de contrato de adhesión",
    text: `Inscrito el ${CONTRACT_PROFECO_REGISTRATION_DATE} en el Registro Público de Contratos de Adhesión de la PROFECO bajo el número ${CONTRACT_PROFECO_NUMBER}.`,
  },
] as const;

export const CONTRACT_ANNEX_A_FIELDS = [
  "Superficie de terreno",
  "Superficie de construcción",
  "Número de recámaras",
  "Número de baños",
  "Estacionamientos",
  "Estado general de conservación",
  "Servicios con los que cuenta",
  "Características relevantes del inmueble",
] as const;

export const CONTRACT_ANNEX_B_ITEMS = [
  "Personalidad del intermediario y autorización para promover la venta",
  "Carta de derechos (Anexo C)",
  "Aviso de privacidad (Anexo E)",
  "Beneficios adicionales",
  "Opciones de pago, con especificación del monto a pagar en cada una",
  "Erogaciones del precio de venta",
  "Condiciones bajo las cuales puede cancelar la operación",
] as const;

export const CONTRACT_ANNEX_C_RIGHTS = [
  "Recibir información y publicidad veraz, clara y actualizada sobre la operación de consumo.",
  "Firmar un contrato de adhesión bajo el modelo inscrito en PROFECO y recibir copia firmada.",
  "Contar con canales y mecanismos de atención gratuitos y accesibles.",
  "Tener a su disposición un Aviso de Privacidad y conocer los mecanismos para ejercer derechos ARCO.",
  "Derecho a la protección de las autoridades competentes, incluyendo presentar denuncias y reclamaciones.",
] as const;

export const CONTRACT_INTEREST_RATE_TEXT =
  "Interés moratorio del 1% mensual simple, calculado multiplicando el saldo vencido por el 1% mensual, dividido entre 30 días y multiplicado por los días naturales de retraso.";
