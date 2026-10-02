package com.c21genera.shared.jobs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.IncompleteEventPublications;

class IncompleteEventResubmitterTest {

  @Test
  @SuppressWarnings("unchecked")
  void eventsLostInADeployAreDeliveredAgainAfterTenMinutes() {
    IncompleteEventPublications publications = mock(IncompleteEventPublications.class);
    ObjectProvider<IncompleteEventPublications> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(publications);

    new IncompleteEventResubmitter(provider).resubmit();

    verify(publications).resubmitIncompletePublicationsOlderThan(IncompleteEventResubmitter.OLDER_THAN);
  }

  @Test
  @SuppressWarnings("unchecked")
  void withoutAnEventRegistryNothingHappens() {
    ObjectProvider<IncompleteEventPublications> provider = mock(ObjectProvider.class);

    new IncompleteEventResubmitter(provider).resubmit();
  }
}
