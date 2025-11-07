package com.sandbox;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TestEntity extends PanacheEntity {
    private String name;
}
