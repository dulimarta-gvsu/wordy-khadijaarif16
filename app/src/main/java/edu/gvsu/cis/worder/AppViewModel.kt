package edu.gvsu.cis.worder

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Letter(val text: Char = '$', val point: Int = 0, val letterMultiplier: Int =1, val wordMultiplier: Int =1)

enum class Origin {
    Stock, CenterBox
}

class AppViewModel(application: Application) : AndroidViewModel(application)  {
    private val _sourceLetters = MutableStateFlow(emptyList<Letter?>())
    val sourceLetters = _sourceLetters.asStateFlow()
    private val _targetLetters = MutableStateFlow(emptyList<Letter?>())
    val targetLetters = _targetLetters.asStateFlow()
    //scrabble point scoring system
    private val letterPoints = mapOf(
        'A' to 1, 'E' to 1, 'I' to 1, 'O' to 1, 'U' to 1, 'L' to 1, 'N' to 1, 'S' to 1, 'T' to 1, 'R' to 1,
        'D' to 2, 'G' to 2,
        'B' to 3, 'C' to 3, 'M' to 3, 'P' to 3,
        'F' to 4, 'H' to 4, 'V' to 4, 'W' to 4, 'Y' to 4,'K' to 5,
        'J' to 8, 'X' to 8, 'Q' to 10, 'Z' to 10
        )
    private val dictionary= mutableSetOf<String>()
    //total words found
    private val _wordsFound= MutableStateFlow(0)
    val wordsFound = _wordsFound.asStateFlow()
    //score of the word
    private val _wordScore =  MutableStateFlow(0)
    val wordScore= _wordScore.asStateFlow()
    //total overall score
    private val _totalScore = MutableStateFlow(0)
    val totalScore= _totalScore.asStateFlow()



    init {
        selectRandomLetters()
        createDictionary()
    }

    fun createDictionary(){
        try{
            getApplication<Application>().assets.open("dictionary.txt")
                .bufferedReader().useLines { lines ->dictionary.addAll(lines.map{it.trim().uppercase()}) }
        }
        catch(e: Exception){e.printStackTrace()}

    }

    //if word exists, true, otherwise false
    fun submitWord():Boolean{
        val word = _targetLetters.value.filterNotNull().map{it.text}.joinToString("").uppercase()
        if (dictionary.contains(word)){
            //have to add scoring
            _totalScore.update { it + _wordScore.value }
            _wordsFound.update { it+1 }
            //clear for next turn
            _targetLetters.value = emptyList()
            _wordScore.value=0
            return true
        }
        else{_wordScore.value =0}
        return false

    }
    fun ReshuffleRemaining(){
        _sourceLetters.update { list -> list.filterNotNull().shuffled() }
    }
    fun selectRandomLetters() {
        _sourceLetters.update {
            // 60% vowels, 40% consonants
            val vowels = (1..6).map {
                "AEIOU".random()
            }
            val consontants = (1..4).map {
                "BCFGHJKLMNPQRSTVWXYZ".random()
            }
            (vowels + consontants).map {
                char ->
                val score = letterPoints[char]?:0
                //gets score from the scores we initialized, if not found then score is 0
                //pick 100 random numbers, if 1-5 then letter multiplier gets 2, if 6-10 then wordmultiplier does
                val rand = (1..100).random()
                val letter = if(rand in 1..5) 2 else 1
                val word = if(rand in 6..10) 2 else 1
                Letter(char,score,letter,word)
            }.shuffled()
        }
        _targetLetters.update { emptyList() }
        _wordScore.value =0
        _totalScore.value=0
        _wordsFound.value=0
    }


    fun calScore(letters: List<Letter>){
        var score =0
        var wMult =1 //by default
        letters.forEach {
            score = score + it.point * it.letterMultiplier
            if (it.wordMultiplier>1){ wMult = wMult*it.wordMultiplier}

        }
        _wordScore.value = score * wMult
    }
    fun rearrangeLetters(group: Origin, arr: List<Letter>) {
        when (group) {
            Origin.Stock -> {
                _sourceLetters.update {
                    arr
                }
            }

            Origin.CenterBox -> {
                _targetLetters.update {
                    arr
                }
                calScore(arr)
            }
        }
    }
}