package com.saktiform.api.repository;

import com.saktiform.api.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    Contact findByPhoneNumber(String phoneNumber);

    Contact findByPhoneNumberAndIdWorkspace(String phoneNumber, Long idWorkspace);

    Contact findByIdAndIdWorkspace(Long id, Long idWorkspace);
}