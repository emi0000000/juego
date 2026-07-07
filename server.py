# server.py
# Instala los requisitos en tu terminal: pip install fastapi uvicorn
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="M.E.N.T.O.S. Core Server")

# Base de datos en memoria (para el proyecto escolar)
game_db = {
    "player_1": {"health": 100, "credits": 2500, "level": 1}
}

class PlayerData(BaseModel):
    health: int
    credits: int
    level: int

@app.get("/api/load_player/{player_id}")
async def load_player(player_id: str):
    return game_db.get(player_id, {"error": "Jugador no encontrado"})

@app.post("/api/save_player/{player_id}")
async def save_player(player_id: str, data: PlayerData):
    game_db[player_id] = data.dict()
    return {"status": "success", "message": "Progreso sincronizado en el servidor"}

# Para ejecutarlo, abre tu terminal y usa: uvicorn server:app --reload