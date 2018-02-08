package com.template

import com.google.common.collect.ImmutableList
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria

abstract class BaseFlow : FlowLogic<UniqueIdentifier>() {
    fun getStateByLinearId(linearId: UniqueIdentifier): StateAndRef<AssetState> {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                null,
                ImmutableList.of(linearId),
                Vault.StateStatus.UNCONSUMED, null)

        return serviceHub.vaultService.queryBy<AssetState>(queryCriteria).states.singleOrNull()
                ?: throw FlowException("Asset with id $linearId not found.")
    }

    fun resolveIdentity(abstractParty: AbstractParty): Party {
        return serviceHub.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
    }


}