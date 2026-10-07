package org.cezmec;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;

public final class Contracts {
    private Contracts() {}
    public enum Role { G, R }
    public enum Reading { OBSERVED, STATE, PERMISSIVE, CAUSATIVE, OTHER }
    public enum Proficiency { NATIVE, FLUENT, LEARNER, UNSPECIFIED }
    public record LanguageInput(
        @NotBlank @Pattern(regexp="[a-zA-Z]{2,8}(-[a-zA-Z0-9]{1,8})*") @Size(max=60) String code,
        @NotBlank @Size(max=120) String name, @NotNull @Size(max=120) String nativeName,
        @NotBlank @Size(max=120) String greenLabel, @NotBlank @Size(max=120) String redLabel,
        @NotBlank @Size(max=400) String starterTemplate,
        @NotNull @Pattern(regexp="ltr|rtl") String direction,
        @NotNull @Size(max=1000) String notes) {}
    public record LanguageView(String code, String name, String nativeName, String greenLabel,
        String redLabel, String starterTemplate, String direction, String notes, int revision, boolean editable) {}
    public record AlternativeInput(
        @NotBlank @Size(max=2000) String annotatedText,
        @NotNull @Size(max=1000) String translation, @NotNull @Size(max=1000) String gloss,
        @NotNull Reading reading, @Min(0) @Max(100) int weight, Set<Role> implicitRoles) {
        public AlternativeInput { implicitRoles = implicitRoles == null ? Set.of() : Set.copyOf(implicitRoles); }
    }
    public record ContributionInput(
        @NotBlank @Size(max=80) String sceneId, @NotBlank @Size(max=20) String sceneVersion,
        @NotBlank @Size(max=60) String languageCode,
        @NotEmpty @Size(max=10) List<@Valid AlternativeInput> alternatives,
        @NotNull @Size(max=120) String dialect, @NotNull Proficiency proficiency,
        @AssertTrue boolean consent, boolean examplesViewed, boolean othersViewed,
        @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String requestId) {}
    public record RatingInput(@Min(0) @Max(100) int weight) {}
    public record ReportInput(@NotBlank @Size(max=1000) String reason) {}
    public record VisibilityInput(@NotNull @Pattern(regexp="VISIBLE|HIDDEN") String visibility) {}
    public record ExpressionView(String id, String sceneId, String sceneVersion, String languageCode,
        String annotatedText, String plainText, String translation, String gloss, String dialect,
        String reading, String proficiency, String implicitRoles, boolean example,
        Double averageWeight, long ratingsCount, Integer myWeight, boolean mine, String createdAt) {}
}
