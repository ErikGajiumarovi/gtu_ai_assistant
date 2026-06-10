package com.gtu.aiassistant.domain.materials.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialSearchHit
import com.gtu.aiassistant.domain.materials.model.MaterialSearchQuery
import com.gtu.aiassistant.domain.model.InfrastructureError

fun interface SearchUserMaterialsPort {
    suspend operator fun invoke(query: MaterialSearchQuery): Either<InfrastructureError, List<MaterialSearchHit>>
}
