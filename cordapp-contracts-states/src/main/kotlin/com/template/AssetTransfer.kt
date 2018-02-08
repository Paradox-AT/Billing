package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class AssetTransfer2(/*val otherSideSession: FlowSession,*/
                     val linearID: UniqueIdentifier,
                     val newOwner: AbstractParty,
                     override val progressTracker: ProgressTracker = AssetTransfer2.tracker()) : BaseFlow2() {
    companion object {

        object PREPARATION : ProgressTracker.Step("Fetching Asset.")
        object BUILDING_TRANSACTION : ProgressTracker.Step("Building Transaction.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying Contract.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing Transaction.")
        object CREATING_SESSION : ProgressTracker.Step("Creating Session for participants.")
        //        object SYNCING : ProgressTracker.Step("Syncing identities.") {
//            override fun childProgressTracker() = IdentitySyncFlow.Send.tracker()
//        }
        object GATHERING_SIGNATURES : ProgressTracker.Step("Gathering Signatures.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Finalising Transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                PREPARATION,
                BUILDING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                CREATING_SESSION,
//                SYNCING,
                GATHERING_SIGNATURES,
                FINALISING_TRANSACTION
        )
    }

    @Suspendable
    override fun call(): UniqueIdentifier {
        progressTracker.currentStep = PREPARATION

        val inputStateAndRef = getStateByLinearId(linearID)
        val inputState = inputStateAndRef.state.data

        val notary = inputStateAndRef.state.notary

        progressTracker.currentStep = BUILDING_TRANSACTION
        val outputState = inputState.withNewOwner(newOwner).ownableState

        val signers = (inputState.participants `union` outputState.participants).map { party -> resolveIdentity(party) }

        val signersKey = signers.map { it.owningKey }

        val command = Command(AssetContract.Commands.Transfer(), signersKey)
        val transactionBuilder = TransactionBuilder(notary).withItems(inputStateAndRef, StateAndContract(outputState, AssetContract.AssetContractID), command)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = CREATING_SESSION
        val participants = signers.minus(listOf(resolveIdentity(inputState.owner)/*,otherSideSession.counterparty*/))
        val sessions = participants.map { party: Party -> initiateFlow(party) }/*.plus(otherSideSession)*/

//        subFlow(IdentitySyncFlow.Send(sessions, signedTransaction.tx, SYNCING.childProgressTracker()))

        println("***********************************************")
        println(inputState)
        println(outputState)
        println(signers)
        println(sessions)
        println("***********************************************")

        progressTracker.currentStep = GATHERING_SIGNATURES
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(signedTransaction, sessions, GATHERING_SIGNATURES.childProgressTracker()))
        progressTracker.currentStep = FINALISING_TRANSACTION
        val finalisedTransaction = subFlow(FinalityFlow(fullySignedTransaction, FINALISING_TRANSACTION.childProgressTracker()))
        return finalisedTransaction.tx.outputsOfType<AssetState>().first().linearId
    }
}


@InitiatedBy(AssetTransfer2::class)
class AssetTransferResponse(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(otherSideSession, progressTracker = SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an Asset state." using (output is AssetState)
            }
        }
        subFlow(signTransactionFlow)
    }
}