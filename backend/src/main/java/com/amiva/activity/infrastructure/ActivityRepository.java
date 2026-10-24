package com.amiva.activity.infrastructure;

import java.util.UUID;

import com.amiva.activity.domain.Activity;
import org.springframework.data.repository.Repository;

// Solo inserción: sin delete ni update expuestos
public interface ActivityRepository extends Repository<Activity, UUID> {

    Activity save(Activity activity);
}
