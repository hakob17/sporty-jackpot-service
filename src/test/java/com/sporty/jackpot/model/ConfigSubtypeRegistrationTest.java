package com.sporty.jackpot.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one thing that can go silently wrong when a configuration is added.
 *
 * <p>Jackson does not derive subtypes from a sealed interface's {@code permits} clause — it only
 * knows what {@code @JsonSubTypes} tells it. A record that is permitted but not registered still
 * <em>serialises</em>, so it would be written to the database happily and then fail to load, with
 * {@code known type ids = []}. That is a poison row, discovered on the next read rather than at the
 * write that caused it.
 *
 * <p>These tests turn that into a red build the moment the two lists disagree.
 */
class ConfigSubtypeRegistrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everyPermittedContributionConfigIsRegisteredWithJackson() {
        assertEveryPermittedSubtypeIsRegistered(ContributionConfig.class);
    }

    @Test
    void everyPermittedRewardConfigIsRegisteredWithJackson() {
        assertEveryPermittedSubtypeIsRegistered(RewardConfig.class);
    }

    private void assertEveryPermittedSubtypeIsRegistered(Class<?> base) {
        Set<Class<?>> permitted = Set.of(base.getPermittedSubclasses());
        Set<Class<?>> registered = Arrays.stream(base.getAnnotation(JsonSubTypes.class).value())
                .map(JsonSubTypes.Type::value)
                .collect(Collectors.toSet());

        assertThat(permitted).as("%s must be sealed over at least one record", base.getSimpleName())
                .isNotEmpty();
        assertThat(registered)
                .as("every record permitted by %s must also appear in its @JsonSubTypes, or it will "
                        + "be written to the database and fail to load", base.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(permitted);
    }

    /**
     * The type names are persisted data — stored jackpots carry them. Renaming one silently breaks
     * every row already written with the old name, so they are pinned here.
     */
    @Test
    void theDiscriminatorNamesAreStable() throws Exception {
        assertThat(mapper.writeValueAsString(new FixedContribution(new BigDecimal("5.00"))))
                .contains("\"type\":\"FIXED\"");
        assertThat(mapper.writeValueAsString(new VariableContribution(new BigDecimal("10.00"),
                new BigDecimal("2.00"), new BigDecimal("1.00"), new BigDecimal("1000.00"))))
                .contains("\"type\":\"VARIABLE\"");
        assertThat(mapper.writeValueAsString(new CappedContribution(new BigDecimal("10.00"),
                new BigDecimal("50.00"))))
                .contains("\"type\":\"CAPPED\"");
        assertThat(mapper.writeValueAsString(new FixedReward(new BigDecimal("10.00"))))
                .contains("\"type\":\"FIXED\"");
        assertThat(mapper.writeValueAsString(new VariableReward(new BigDecimal("1.00"),
                new BigDecimal("2.00"), new BigDecimal("1000.00"), new BigDecimal("20000.00"))))
                .contains("\"type\":\"VARIABLE\"");
    }

    @Test
    void aConfigurationSurvivesTheRoundTripThroughJson() throws Exception {
        ContributionConfig contribution = new VariableContribution(new BigDecimal("10.00"),
                new BigDecimal("2.00"), new BigDecimal("1.00"), new BigDecimal("1000.00"));
        RewardConfig reward = new VariableReward(new BigDecimal("1.00"), new BigDecimal("2.00"),
                new BigDecimal("1000.00"), new BigDecimal("20000.00"));

        assertThat(mapper.readValue(mapper.writeValueAsString(contribution), ContributionConfig.class))
                .isEqualTo(contribution);
        assertThat(mapper.readValue(mapper.writeValueAsString(reward), RewardConfig.class))
                .isEqualTo(reward);
    }

    @Test
    void aCappedConfigurationSurvivesTheRoundTripThroughJson() throws Exception {
        CappedContribution capped = new CappedContribution(new BigDecimal("10.00"), new BigDecimal("50.00"));
        ContributionConfig deserialized = mapper.readValue(mapper.writeValueAsString(capped), ContributionConfig.class);

        assertThat(deserialized).isEqualTo(capped);
        assertThat(deserialized).isInstanceOf(CappedContribution.class);
    }
}
