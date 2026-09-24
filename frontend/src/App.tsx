import { type FormEvent, useState } from 'react'
import './App.css'

type PhraseCheckResponse = {
  originalPhrase: string
  correct: boolean
  correctedPhrase: string
  explanation: string
  provider: string
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

function App() {
  const [phrase, setPhrase] = useState('')
  const [result, setResult] = useState<PhraseCheckResponse | null>(null)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const submittedPhrase = phrase.trim()
    if (!submittedPhrase || isLoading) {
      return
    }

    setIsLoading(true)
    setResult(null)
    setError('')

    try {
      const response = await fetch(`${apiBaseUrl}/api/check`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ phrase: submittedPhrase }),
      })

      if (!response.ok) {
        throw new Error(`The server returned ${response.status}.`)
      }

      setResult((await response.json()) as PhraseCheckResponse)
    } catch (requestError) {
      const message =
        requestError instanceof Error ? requestError.message : 'Unknown error'
      setError(`Could not check the phrase. ${message}`)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <main className="page">
      <section className="checker">
        <h1>Polish Writing Lab</h1>

        <form onSubmit={handleSubmit}>
          <div className="phrase-input">
            <label htmlFor="phrase">Enter a Polish phrase</label>
            <input
              id="phrase"
              name="phrase"
              type="text"
              value={phrase}
              onChange={(event) => setPhrase(event.target.value)}
              placeholder="Wpisz zdanie po polsku"
              maxLength={500}
              autoComplete="off"
              autoFocus
            />
            <button
              type="submit"
              disabled={!phrase.trim() || isLoading}
              aria-label="Check phrase"
            >
              {isLoading ? <span className="spinner" /> : '→'}
            </button>
          </div>
        </form>

        <div className="result-region" aria-live="polite">
          {error && <p className="error">{error}</p>}

          {result && (
            <article className={`result ${result.correct ? 'correct' : 'incorrect'}`}>
              <p className="result-status">
                {result.correct ? 'Looks correct' : 'Suggested correction'}
              </p>
              {!result.correct && (
                <p className="corrected-phrase">{result.correctedPhrase}</p>
              )}
              <p className="explanation">{result.explanation}</p>
              <p className="provider">{result.provider} model response</p>
            </article>
          )}
        </div>
      </section>
    </main>
  )
}

export default App
