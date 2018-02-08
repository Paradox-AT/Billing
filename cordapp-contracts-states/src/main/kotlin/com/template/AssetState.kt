package com.template

import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.finance.contracts.asset.CASH
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*


data class AssetState(
        val type: String,
        val quantity: Int,
        val faceValue: Amount<Currency>,
        val issuance: AbstractParty,
        override val owner: AbstractParty,
        override val participants: List<AbstractParty>
) : OwnableState, LinearState {
    override val linearId: UniqueIdentifier
    var dueDate: Instant

    init {
        requireThat {
            "The number of asset shouldn't be empty." using (quantity > 0)
            "Type of asset shouldn't be null" using (type.isNotBlank())
            "The asset should hold some value" using (faceValue.CASH.amount.quantity > 0)
        }
        linearId = UniqueIdentifier()
        dueDate = Instant.MAX
    }

    constructor(type: String,
                quantity: Int,
                faceValue: Amount<Currency>,
                issuance: AbstractParty,
                owner: AbstractParty
    ) : this(type, quantity, faceValue, issuance, owner, listOf(owner))

    fun withNewParticipant(newParticipant: AbstractParty): CommandAndState {
        val newState = copy(participants = (this.participants `union` listOf(newParticipant)).toList())
        return CommandAndState(AssetContract.Commands.Issue(), newState)
    }

    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        val newState = copy(owner = newOwner, participants = listOf(newOwner, issuance))
        val today = LocalDate.now()
        newState.dueDate = LocalDate.of(today.year, today.month.plus(1), 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        println(Instant.now())
        return CommandAndState(AssetContract.Commands.Transfer(), newState)
    }

    fun withoutOwnerAndParticipant() = copy(owner = AnonymousParty(NullKeys.NullPublicKey), participants = listOf())

}
