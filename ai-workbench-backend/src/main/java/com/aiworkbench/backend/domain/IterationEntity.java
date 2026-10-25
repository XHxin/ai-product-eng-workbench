package com.aiworkbench.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name="iterations")
public class IterationEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Version public long revision;
    public String name;
    public String startDate;
    public String endDate;
    public String status="PLANNING";
}
