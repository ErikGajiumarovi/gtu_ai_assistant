package com.gtu.aiassistant.application.materials

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.materials.model.MaterialCollection
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionCommand
import com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionError
import com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionResult
import com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionUseCase
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionCommand
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionError
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionResult
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionUseCase
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsError
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsQuery
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsResult
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsUseCase
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialCollectionPort
import java.time.Instant
import java.util.UUID

class CreateMaterialCollectionUseCaseImpl(
    private val saveMaterialCollectionPort: SaveMaterialCollectionPort
) : CreateMaterialCollectionUseCase {
    override suspend fun invoke(command: CreateMaterialCollectionCommand): Either<CreateMaterialCollectionError, CreateMaterialCollectionResult> =
        either {
            val now = Instant.now()
            val collection = MaterialCollection.create(
                id = MaterialCollectionId.create(UUID.randomUUID())
                    .mapLeft(CreateMaterialCollectionError::InvalidDomainState)
                    .bind(),
                version = 0L,
                ownerUserId = command.ownerUserId,
                name = command.name,
                createdAt = now,
                updatedAt = now
            ).mapLeft(CreateMaterialCollectionError::InvalidDomainState).bind()

            CreateMaterialCollectionResult(
                collection = saveMaterialCollectionPort(collection)
                    .mapLeft(CreateMaterialCollectionError::PersistenceFailed)
                    .bind()
            )
        }
}

class ListMaterialCollectionsUseCaseImpl(
    private val findMaterialCollectionPort: FindMaterialCollectionPort
) : ListMaterialCollectionsUseCase {
    override suspend fun invoke(query: ListMaterialCollectionsQuery): Either<ListMaterialCollectionsError, ListMaterialCollectionsResult> =
        either {
            val result = findMaterialCollectionPort(FindMaterialCollectionPort.Strategy.ByOwner(query.ownerUserId))
                .mapLeft(ListMaterialCollectionsError::PersistenceFailed)
                .bind()

            ListMaterialCollectionsResult(
                collections = (result as FindMaterialCollectionPort.Result.Multiple).collections
            )
        }
}

class DeleteMaterialCollectionUseCaseImpl(
    private val findMaterialCollectionPort: FindMaterialCollectionPort,
    private val deleteMaterialCollectionPort: DeleteMaterialCollectionPort
) : DeleteMaterialCollectionUseCase {
    override suspend fun invoke(command: DeleteMaterialCollectionCommand): Either<DeleteMaterialCollectionError, DeleteMaterialCollectionResult> =
        either {
            val result = findMaterialCollectionPort(
                FindMaterialCollectionPort.Strategy.ById(command.ownerUserId, command.collectionId)
            ).mapLeft(DeleteMaterialCollectionError::PersistenceFailed).bind()
            val collection = (result as FindMaterialCollectionPort.Result.Single).collection
            ensure(collection != null) { DeleteMaterialCollectionError.CollectionNotFound }

            deleteMaterialCollectionPort(command.ownerUserId, command.collectionId)
                .mapLeft(DeleteMaterialCollectionError::PersistenceFailed)
                .bind()

            DeleteMaterialCollectionResult(deleted = true)
        }
}
