package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*


@InitiatingFlow
@StartableByRPC
class AssetDeclare(
        val type: String,
        val quantity: Int,
        val faceValue: Amount<Currency>
) : BaseFlow() {
    companion object {

        object PREPARATION : ProgressTracker.Step("Obtaining Notary.")
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating Transaction.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying Contract.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing Transaction")
        object FINALISING_TRANSACTION : ProgressTracker.Step("Finalising Transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                PREPARATION,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): UniqueIdentifier {
        progressTracker.currentStep = PREPARATION
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = GENERATING_TRANSACTION
        val outputState = AssetState(type, quantity, faceValue, ourIdentity, ourIdentity)
        val command = Command(data = AssetContract.Commands.Declare(), key = ourIdentity.owningKey)
        val transactionBuilder = TransactionBuilder(notary)
//                .withItems(StateAndContract(outputState, TEMPLATE_CONTRACT_ID),command)
                .addOutputState(outputState, AssetContract.AssetContractID)
                .addCommand(command)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = FINALISING_TRANSACTION
        val finalisedTransaction = subFlow(FinalityFlow(signedTransaction, FINALISING_TRANSACTION.childProgressTracker()))
        return finalisedTransaction.tx.outputsOfType<AssetState>().first().linearId
    }
}
