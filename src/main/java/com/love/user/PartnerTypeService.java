package com.love.user;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PartnerTypeService {

    private final JdbcTemplate jdbcTemplate;

    public Optional<String> getPartnerType(Long userId) {
        try {
            String value = jdbcTemplate.queryForObject(
                    """
                    select result_value
                    from test_results
                    where user_id = ? and test_key = 'partner_type'
                    order by id desc
                    limit 1
                    """,
                    String.class,
                    userId
            );
            return Optional.ofNullable(value);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
