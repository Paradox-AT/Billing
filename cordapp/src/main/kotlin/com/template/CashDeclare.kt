package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Instant
import java.util.*


@InitiatingFlow
@StartableByRPC
class CashDeclare(val amount: Amount<Currency>,
                  val party: AbstractParty?,
                  val dueInstant: Instant?,
                  var assetLinearID: String,
                  override val progressTracker :ProgressTracker = CashDeclare.tracker()): BaseFlow() {
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

    constructor(amount: Amount<Currency>) : this(amount, null, null, String())



    @Suspendable
    override fun call(): UniqueIdentifier {
        progressTracker.currentStep = PREPARATION
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = GENERATING_TRANSACTION
        val outputState = if (party != null && dueInstant != null) CashState(amount, ourIdentity, dueInstant, party, assetLinearID, ourIdentity, listOf(ourIdentity, party))
        else CashState(amount, ourIdentity, ourIdentity)
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
