package com.hama.domain.auth.repository;

import com.hama.domain.auth.entity.EmailInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailInquiryRepository extends JpaRepository<EmailInquiry, Long> {
}
