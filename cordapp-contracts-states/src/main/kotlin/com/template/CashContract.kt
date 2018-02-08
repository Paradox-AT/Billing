package com.template

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.transactions.LedgerTransaction


open class CashContract : Contract {
    companion object {
        val CashContractID = "com.template.CashContract"
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class Pay : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
    }
}