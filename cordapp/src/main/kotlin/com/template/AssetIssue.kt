package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

@InitiatingFlow
@StartableByRPC
class AssetIssue(
        val linearID: UniqueIdentifier,
//        val linearID: String,
        val newParticipant: Party
) : BaseFlow() {
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

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): UniqueIdentifier {
        progressTracker.currentStep = PREPARATION
        val inputStateAndRef = getStateByLinearId(linearID)
//        val inputStateAndRef = getStateByLinearId(UniqueIdentifier.fromString(linearID))
        val inputState = inputStateAndRef.state.data

        val notary = inputStateAndRef.state.notary

        val owner = getOwnerIdentity(inputState)
        check(owner == ourIdentity) { "Asset can ony be transferred by the owner" }
        progressTracker.currentStep = BUILDING_TRANSACTION
        val outputState = inputState.withNewParticipant(newParticipant).ownableState

        val signers = outputState.participants.map { party -> resolveIdentity(party) }

        val signersKey = signers.map { it.owningKey }

        val command = Command(AssetContract.Commands.Issue(), signersKey)
        val transactionBuilder = TransactionBuilder(notary).withItems(inputStateAndRef, StateAndContract(outputState, AssetContract.AssetContractID), command)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = CREATING_SESSION
        val participants = signers.minus(resolveIdentity(inputState.owner))
        val sessions = participants.map { party: Party -> initiateFlow(party) }

//        subFlow(IdentitySyncFlow.Send(sessions, signedTransaction.tx, SYNCING.childProgressTracker()))

        progressTracker.currentStep = GATHERING_SIGNATURES
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(signedTransaction, sessions, GATHERING_SIGNATURES.childProgressTracker()))

        progressTracker.currentStep = FINALISING_TRANSACTION
        val finalisedTransaction = subFlow(FinalityFlow(fullySignedTransaction, FINALISING_TRANSACTION.childProgressTracker()))
        return finalisedTransaction.tx.outputsOfType<AssetState>().first().linearId
    }

    @Suspendable
    private fun getOwnerIdentity(inputState: AssetState): AbstractParty {
        return if (inputState.owner is AnonymousParty) {
            resolveIdentity(inputState.owner)
        } else {
            inputState.owner
        }
    }
}


@InitiatedBy(AssetIssue::class)
class AssetIssueResponse(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()
    lateinit var outputState: AssetState
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(otherSideSession, progressTracker = SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) {
                outputState = stx.tx.outputStates.single() as AssetState
                requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Asset state." using (output is AssetState)
                }
            }
        }
        subFlow(signTransactionFlow)
//        val dueInstant = LocalDate.now().plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val dueInstant =  Instant.now().plusMillis(10000)
        subFlow(CashDeclare(outputState.faceValue, outputState.issuance,dueInstant,outputState.linearId.toString()))
    }
}