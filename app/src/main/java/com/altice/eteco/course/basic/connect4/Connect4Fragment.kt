package com.altice.eteco.course.basic.connect4

import android.os.Bundle
import android.view.View

import com.altice.eteco.course.basic.BaseFragment
import com.altice.eteco.course.basic.R
import com.altice.eteco.course.basic.simonSays.play

import com.jakewharton.rxbinding2.view.clicks

import com.pawegio.kandroid.views
import com.trello.rxlifecycle2.android.FragmentEvent

import io.reactivex.rxkotlin.merge
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.Observable.merge
import io.reactivex.android.schedulers.AndroidSchedulers

import kotlinx.android.synthetic.main.connect_four_fragment.*
import java.util.concurrent.TimeUnit

class Connect4Fragment : BaseFragment() {

    override val layoutRes: Int = R.layout.connect_four_fragment
    override val titleRes : Int = R.string.conn4_title

    val board = PublishSubject.create<List<Bucket>>()
    val turn  = BehaviorSubject.createDefault(Turn.Red)
    val wins  = PublishSubject.create<Turn>()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val views = grid.views
        
        // Collect chips.
        val chips = grid.views.map {

            val tag = it.tag.toString()
            val column   = "${tag.first()}".toInt()
            val row   = "${tag.last()}".toInt()

            Chip(views.indexOf(it), it, column, row)
        }

        // Winner stream.
        val winner = wins.map { w ->
            { ->
                // Disabling all chips
                views.forEach {
                    it.isEnabled  = false
                    it.alpha      = .4f
                }

                // Increase counter
                when (w)  {

                    Turn.Red    -> {
                        red.bump()
                        R.raw.applause.play(context) {
                            doReset(views)
                        }
                    }

                    Turn.Yellow -> {
                        yellow.bump()
                        R.raw.applause.play(context) {
                            doReset(views)
                        }
                    }

                    // Reset when is black,
                    Turn.Black -> doReset(views)
                }
            }
        }

        // Restart stream.
        val restart = reset.clicks().map { _ ->
            {
                doReset(views)
            }
        }

        // Player turn stream.
        val game =
            chips.map { chip ->
                chip.view.clicks().map { chip }
            }
            .merge()
            .throttleFirst(400, TimeUnit.MILLISECONDS)
            .withLatestFrom(board.startWith(emptyList<Bucket>())) {
                chip, board -> Pair(chip, board)
            }.withLatestFrom(
                turn.doOnNext {
                    onTurn.background = it.drawable(context)
                }
            ) {
                (chip, board), t -> Triple(chip, board, t)
            }
            .map {
                (chip, board, turn) ->
                    val bucket  = Game.findBucket(turn, chip, board)
                    val newChip = chips.find { it.row == bucket.row && it.column == bucket.column }
                    Triple(Pair(newChip, bucket), board, turn)
            }
            .filter {
                (pair, board, _) ->
                    val chip = pair.first
                    board.isEmpty() || board.none { it.row == chip?.row && it.column == chip.column }
            }
            .doOnNext {
                (pair, bucket, _) ->
                    val newBucket = pair.second
                    board.onNext(bucket + newBucket)
            }
            .map { (pair, b, t) -> {
                ->
                    pair.first?.let {
                        it.view.background = t.drawable(context)
                    }

                    turn.onNext(t.flip())

                    val tmp = b + pair.second

                    if (Game.check(t, tmp))
                        wins.onNext(t)
                    else if (tmp.size == 42)
                        wins.onNext(Turn.Black)
                }
            }

        // Game flow.
        merge(game, restart, winner)
            .observeOn(AndroidSchedulers.mainThread())
            .compose(bindUntilEvent(FragmentEvent.DESTROY_VIEW))
            .subscribe {
                it?.invoke()
            }
    }

    fun doReset(views: List<View>) {
        val bg = Turn.Black.drawable(context)
        views.forEach {
            it.background = bg
            it.isEnabled  = true
            it.alpha      = 1f
        }
        board.onNext(emptyList())
        turn.onNext(Turn.Red)
    }

}