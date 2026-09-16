// Referencia textual del aviso de privacidad simplificado para recepción de
// documentos, tomado de:
// "Aviso_Privacidad_C21_Genera_Recepcion_Documentos_logo_autorizado.docx"
//
// Este es el aviso que rige el flujo del cliente en este módulo (recepción
// documental y revisión de viabilidad). No se combina con el aviso integral
// ni se corrigen las diferencias de domicilio respecto al contrato — cada
// documento fuente conserva sus propios datos.

export const PRIVACY_TITLE = "Aviso de privacidad simplificado";
export const PRIVACY_SUBTITLE = "Recepción de documentos y revisión de viabilidad inmobiliaria";

export const PRIVACY_RESPONSIBLE_LEGAL_NAME = "Grupo WILGEN y Asociados, S. de R.L. de C.V.";
export const PRIVACY_RESPONSIBLE_COMMERCIAL_NAME = "CENTURY 21 Genera";
export const PRIVACY_RESPONSIBLE_RFC = "GWA260224B11";
export const PRIVACY_RESPONSIBLE_ADDRESS =
  "Alta Tensión 403, Local 1, Acapatzingo, C.P. 62448, Cuernavaca, Morelos.";
export const PRIVACY_RESPONSIBLE_EMAIL = "ventas@c21genera.com";
export const PRIVACY_RESPONSIBLE_PHONE = "777 454 0198";
export const PRIVACY_INTEGRAL_NOTICE_URL = "century21mexico.com/avisodeprivacidad/649";

export const PRIVACY_INTRO =
  "Este aviso se firma cuando el titular entrega información y documentos para analizar si su inmueble puede ser tomado en promoción, intermediación, venta o renta por CENTURY 21 Genera. El uso de sus datos será limitado a esta revisión y, en su caso, a la preparación de la contratación del servicio inmobiliario.";

export const PRIVACY_SECTIONS = [
  {
    id: "datos",
    title: "1. Datos y documentos que podrán solicitarse",
    text: "Identificación y contacto: nombre, domicilio, teléfono, correo, INE/pasaporte, CURP, RFC, estado civil y, en su caso, datos del cónyuge, copropietarios, herederos, usufructuarios, apoderados o representantes. Documentos del inmueble: escritura, predial, agua, luz, plano catastral, avalúo, certificado de libertad de gravamen, uso de suelo, régimen de condominio, poderes, sucesiones, adjudicaciones, testamentos, hipotecas, adeudos, gravámenes, contratos de arrendamiento y demás documentos necesarios para la revisión.",
  },
  {
    id: "finalidades-principales",
    title: "2. Finalidades principales",
    text: "Identificar al titular o persona autorizada; revisar titularidad y situación jurídica, fiscal, catastral, documental y comercial; integrar expediente interno; emitir observaciones, análisis de factibilidad o estrategia de precio; solicitar información complementaria; coordinar visitas, fotografías, ficha técnica, publicación y, si procede, contrato de intermediación. También podrán tratarse para cumplir obligaciones legales, fiscales, administrativas, de protección al consumidor, NOM-247-SE-2021 y prevención de operaciones con recursos de procedencia ilícita aplicables a la actividad inmobiliaria.",
  },
  {
    id: "finalidades-secundarias",
    title: "3. Finalidades secundarias",
    text: "Enviar información inmobiliaria, reportes de mercado o seguimiento comercial posterior. Puede negarse marcando la casilla correspondiente, de forma independiente al consentimiento principal.",
  },
  {
    id: "transferencias",
    title: "4. Transferencias y acceso autorizado",
    text: "La información podrá compartirse solo cuando sea necesario con asesores, personal administrativo y directivo de CENTURY 21 Genera; notarías, abogados, gestores, valuadores, brokers aliados, plataformas internas, CRM, almacenamiento digital, posibles interesados en la operación y autoridades competentes cuando exista obligación legal. No se venderán, rentarán ni comercializarán los datos personales.",
  },
  {
    id: "seguridad",
    title: "5. Seguridad y conservación",
    text: "La documentación será tratada con confidencialidad y medidas razonables de seguridad administrativa, técnica y física. Solo tendrá acceso personal autorizado que requiera la información para la revisión o prestación del servicio. Si no se contratan los servicios, puede solicitarse devolución, cancelación o eliminación, salvo información que deba conservarse por obligación legal, comprobación interna o defensa de derechos.",
  },
  {
    id: "arco",
    title: "6. Derechos ARCO y revocación",
    text: "El titular puede acceder, rectificar, cancelar u oponerse al tratamiento de sus datos, así como revocar su consentimiento, enviando solicitud al correo señalado con nombre, medio de respuesta, identificación oficial y descripción clara de los datos o documentos relacionados.",
  },
] as const;

export const PRIVACY_CONSENT_TEXT =
  "Declaro que leí este aviso y autorizo el tratamiento de mis datos personales, datos patrimoniales y documentos del inmueble conforme a las finalidades aquí indicadas.";

export const PRIVACY_SECONDARY_OPT_OUT_TEXT =
  "No autorizo finalidades secundarias o promocionales.";

export const PRIVACY_RECEIVED_DOCUMENTS_CHECKLIST = [
  "INE",
  "Escritura",
  "Predial",
  "Agua",
  "CFE",
  "Avalúo",
  "Plano",
  "Régimen de condominio",
  "Fotos",
  "Otros",
] as const;

export const PRIVACY_SIGNATURE_DISCLAIMER =
  "Firma simulada para fines del prototipo. No constituye una firma electrónica certificada.";
