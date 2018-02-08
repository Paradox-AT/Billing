package com.template

import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import java.time.Instant
import java.util.*

data class CashState(
        val amount: Amount<Currency>,
        val issuance: AbstractParty,
        val nextActivityTime: Instant,
        val newOwner: AbstractParty,
        val assetlinearID: String,
        override val owner: AbstractParty,
        override val participants: List<AbstractParty>
) : OwnableState, LinearState, SchedulableState {

    override val linearId: UniqueIdentifier

    init {
        requireThat {
            "The amount issued shouldn't be empty." using (amount.quantity > 0)
        }
        linearId = UniqueIdentifier()
    }

    constructor(
            amount: Amount<Currency>,
            issuance: AbstractParty,
            owner: AbstractParty
    ) : this(amount, issuance, Instant.MAX, owner,"INVALID", owner, listOf(owner))

    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(CashContract.Commands.Pay(), copy(owner = newOwner, nextActivityTime = Instant.MAX))
    }

    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
        return ScheduledActivity(flowLogicRefFactory.create(CashTransfer2::class.java, thisStateRef), nextActivityTime)
    }
}
