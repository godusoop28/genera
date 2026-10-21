package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.online21.PropertyDossier;
import com.c21genera.online21.PropertyDossier.Details;
import com.c21genera.online21.PropertyDossier.FollowUp;
import com.c21genera.online21.PropertyDossier.PortalListing;
import com.c21genera.online21.PropertyDossier.RelatedClient;
import com.c21genera.online21.PropertyDossier.SheetVariant;
import com.c21genera.online21.PropertyListings;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Detalle de una propiedad de 21Online en el CRM (como su pantalla "Ver propiedad"): datos
 * generales, portales, clientes relacionados, seguimientos y ficha técnica en PDF.
 *
 * <p>Los datos de contacto de los clientes interesados solo los ve quien ve todos los leads o el
 * asesor de la propiedad; los demás ven el resumen (medio, estatus y fecha) sin nombre ni teléfono.
 */
@Service
public class PropertyDossierService {

  private final PropertyDossier dossier;
  private final PropertyListings listings;
  private final AdvisorProfileRepository profiles;
  private final StaffNames staff;

  PropertyDossierService(PropertyDossier dossier, PropertyListings listings, AdvisorProfileRepository profiles, StaffNames staff) {
    this.dossier = dossier;
    this.listings = listings;
    this.profiles = profiles;
    this.staff = staff;
  }

  /** generaAdvisor: el usuario de Genera vinculado al asesor de 21Online (puede ser null). */
  public record DetailsView(Details details, PropertyService.Person generaAdvisor) {}

  /** contactsVisible: si se muestran nombre, teléfono, correo y mensaje de cada cliente. */
  public record ClientsView(boolean contactsVisible, List<RelatedClient> clients) {}

  public record Sheet(String fileName, byte[] pdf) {}

  public DetailsView details(long id) {
    Details d = call(() -> dossier.details(id)).orElseThrow(() -> new NotFoundException("Propiedad de 21Online", id));
    return new DetailsView(d, generaAdvisorOf(d.listing().advisorOnline21Id()));
  }

  public List<PortalListing> portals(long id) {
    return call(() -> dossier.portals(id));
  }

  public ClientsView clients(long id, CurrentUser user) {
    List<RelatedClient> clients = call(() -> dossier.relatedClients(id));
    boolean visible = canSeeContacts(id, user);
    if (visible) {
      return new ClientsView(true, clients);
    }
    return new ClientsView(
        false,
        clients.stream()
            .map(c -> new RelatedClient(c.id(), null, null, null, c.source(), c.status(), c.type(), null, c.createdAt()))
            .toList());
  }

  public List<FollowUp> followUps(long id) {
    return call(() -> dossier.followUps(id));
  }

  public Sheet sheet(long id, SheetVariant variant) {
    byte[] pdf = call(() -> dossier.sheet(id, variant)).orElseThrow(() -> new NotFoundException("Propiedad de 21Online", id));
    String suffix =
        switch (variant) {
          case ADVISOR -> "";
          case NO_CONTACT -> "-sin-contacto";
          case NO_LOGO -> "-sin-logo";
        };
    return new Sheet("ficha-" + id + suffix + ".pdf", pdf);
  }

  private boolean canSeeContacts(long id, CurrentUser user) {
    if (user.hasPermission("LEAD_VIEW_ALL")) {
      return true;
    }
    Long advisor = call(() -> listings.get(id)).map(PropertyListings.Listing::advisorOnline21Id).orElse(null);
    PropertyService.Person person = generaAdvisorOf(advisor);
    return person != null && Objects.equals(person.id(), user.id());
  }

  private PropertyService.Person generaAdvisorOf(Long online21UserId) {
    if (online21UserId == null) {
      return null;
    }
    return profiles.findByOnline21UserId(online21UserId).map(AdvisorProfile::getUserId).map(this::person).orElse(null);
  }

  private PropertyService.Person person(UUID userId) {
    return new PropertyService.Person(userId, staff.nameOf(userId, staff.names()));
  }

  private <T> T call(Supplier<T> action) {
    if (!listings.available()) {
      throw new UnprocessableException("ONLINE21_NOT_CONFIGURED", "La integración con 21Online no está configurada.");
    }
    try {
      return action.get();
    } catch (NotFoundException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new UnprocessableException("ONLINE21_UNAVAILABLE", "21Online no respondió. Intenta de nuevo en un momento.");
    }
  }
}
