#!/usr/bin/env python3
from pathlib import Path
import json
root=Path(__file__).parent/'seriesguide-fixtures'
shows=json.loads((root/'seriesguide-shows-backup.json').read_text())
movies=json.loads((root/'seriesguide-movies-backup.json').read_text())
lists=json.loads((root/'seriesguide-lists-backup.json').read_text())
assert isinstance(shows,list) and shows and 'seasons' in shows[0]
assert isinstance(movies,list) and movies and ('tmdb_id' in movies[0] or 'imdb_id' in movies[0])
assert isinstance(lists,list) and lists and 'items' in lists[0]
assert any('episodes' in season for show in shows for season in show.get('seasons',[]))
allowed={'tmdb-show','show','movie','tmdb-movie','imdb-movie','episode','season'}
for l in lists:
    for item in l.get('items',[]):
        assert item.get('type') in allowed, item
print('OK SeriesGuide fixtures: shows, movies, lists and supported list item types')
