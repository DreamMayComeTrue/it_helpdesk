package com.rootcause.fixdesk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rootcause.fixdesk.audit.AuditDao;
import com.rootcause.fixdesk.audit.AuditEntry;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditServiceImplTest {

    private final AuditDao dao = mock(AuditDao.class);
    private final AuditServiceImpl service = new AuditServiceImpl(dao);

    @Test
    void writesOneRowWithTheGivenValues() {
        service.log(7L, "TICKET_CREATED", "TICKET", 15L, "Projector in Lab 3", "10.0.0.1");

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(dao).insert(entry.capture());
        assertThat(entry.getValue()).isEqualTo(
                new AuditEntry(null, 7L, "TICKET_CREATED", "TICKET", 15L, "Projector in Lab 3", "10.0.0.1", null));
    }

    @Test
    void systemEventsMayHaveNoActorEntityOrIp() {
        service.log(null, "AI_ACTION_EXPIRED", null, null, null, null);

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(dao).insert(entry.capture());
        assertThat(entry.getValue().actorId()).isNull();
        assertThat(entry.getValue().action()).isEqualTo("AI_ACTION_EXPIRED");
    }

    @Test
    void longDetailIsCutToTheColumnSize() {
        service.log(1L, "ADD_NOTE", "TICKET", 2L, "x".repeat(1500), "10.0.0.1");

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(dao).insert(entry.capture());
        assertThat(entry.getValue().detail()).hasSize(1000);
    }

    @Test
    void actionIsRequired() {
        assertThatThrownBy(() -> service.log(1L, " ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dao);
    }
}
