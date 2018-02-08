package com.template

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

open class AssetContract : Contract {
    companion object {
        val AssetContractID = "com.template.AssetContract"
    }

    interface Commands : CommandData {
        class Declare : TypeOnlyCommandData(), Commands
        class Issue : TypeOnlyCommandData(), Commands
        class Transfer : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        /*val commands = tx.commandsOfType<Commands>()
        var setOfSigners: Set<PublicKey>
        println()
        println("Commands ="+commands.size)
        println(commands)
        println()
        for (command in commands) {
            setOfSigners = command.signers.toSet()
            try {
                when (command.value) {
                    is Commands.Declare -> verifyDeclare(tx)
                    is Commands.Issue -> verifyIssue(tx, setOfSigners)
                    is Commands.Transfer -> verifyTransfer(tx, setOfSigners)
                    else -> throw IllegalArgumentException("Unrecognised command")
                }
            } catch (e: Exception) {
                println("***********************")
                println(e)
                println(command)
                println(setOfSigners.size)
                println(tx.inputStates)
                println(tx.outputStates)
                println("***********************")
            }
        }*/
    }


    private fun verifyDeclare(transaction: LedgerTransaction) = requireThat {
        "No inputs should be consumed when issuing an asset." using (transaction.inputStates.isEmpty())
        "Only one asset state should be created when issuing an asset." using (transaction.outputStates.size == 1)
    }

    private fun verifyIssue(transaction: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val input = transaction.inputsOfType<AssetState>().single()
        val output = transaction.outputsOfType<AssetState>().single()

        "An asset transfer transaction should only consume one input state." using (transaction.inputs.size == 1)
        "Only one asset state should be created when introducing a participant." using (transaction.outputs.size == 1)
/*                           */"Only the participants property may change." using (input.withoutOwnerAndParticipant() == output.withoutOwnerAndParticipant())
        "Participants must change in a transfer." using (input.participants != output.participants)
        "All participants must sign the transaction" using (signers == getSigners(input, output))
    }

    private fun verifyTransfer(transaction: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val input = transaction.inputsOfType<AssetState>().single()
        val output = transaction.outputsOfType<AssetState>().single()

        "An asset transfer transaction should only consume one input state." using (transaction.inputs.size == 1)
        "Only one asset state should be created when issuing an asset." using (transaction.outputs.size == 1)
        "Only the owner and participants property may change." using (input.withoutOwnerAndParticipant() == output.withoutOwnerAndParticipant())
        "The ownership must change in a transfer." using (input.owner != output.owner)


        "All participants must sign the transaction" using (signers == getSigners(input, output))

    }

    private fun getSigners(input: AssetState, output: AssetState): Set<PublicKey> {
        return input.participants.map { it.owningKey } `union` output.participants.map { it.owningKey }
    }
}