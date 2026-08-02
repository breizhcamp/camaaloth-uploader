#!/usr/bin/env python

# Vue d'ensemble
# Script Python qui fusionne les données vidéo depuis un fichier source vers un fichier de planning en utilisant les IDs comme clés de correspondance.
# Arguments en ligne de commande
#
# --schedule-source : Chemin vers le fichier JSON source contenant le planning
# --source-url : Chemin vers le fichier JSON contenant les données vidéo (ID + URL)
# --schedule-target : Chemin vers le fichier JSON de destination (sera écrasé)
#
# Logique de traitement
# 1. Lecture du fichier source-url
#
# Charger le contenu JSON du fichier --source-url
# Extraire pour chaque élément :
#
# Champ id (clé)
# Champ video_url (valeur)
# Construire un dictionnaire {id: video_url}
#
# 2. Traitement du fichier schedule-source
#
# Charger le contenu JSON du fichier --schedule-source
# Pour chaque élément :
#
# Vérifier si l'ID existe dans le dictionnaire des URLs
# Si oui : ajouter/mettre à jour le champ video_url
# Si non : laisser l'élément inchangé
#
# 3. Écriture du résultat
#
# Sauvegarder le contenu modifié dans --schedule-target
# Format : JSON avec indentation pour lisibilité
#
# Gestion d'erreurs
#
# Vérifier l'existence des fichiers d'entrée
# Valider le format JSON des fichiers
# Gérer les champs manquants (id, video_url)
# Messages d'erreur explicites
# Code de sortie approprié

import argparse
import json
import os
import sys

def main():
    # Parse command line arguments
    parser = argparse.ArgumentParser(description='Merge video URLs into a schedule file')
    parser.add_argument('--schedule-source', required=True, help='Path to the source JSON file containing the schedule')
    parser.add_argument('--source-url', required=True, help='Path to the JSON file containing video data (ID + URL)')
    parser.add_argument('--schedule-target', required=True, help='Path to the destination JSON file (will be overwritten)')
    args = parser.parse_args()

    # Check if input files exist
    if not os.path.exists(args.schedule_source):
        print(f"Error: Schedule source file '{args.schedule_source}' does not exist", file=sys.stderr)
        sys.exit(1)

    if not os.path.exists(args.source_url):
        print(f"Error: Source URL file '{args.source_url}' does not exist", file=sys.stderr)
        sys.exit(1)

    try:
        # 1. Read the source-url file
        with open(args.source_url, 'r', encoding='utf-8') as f:
            try:
                url_data = json.load(f)
            except json.JSONDecodeError:
                print(f"Error: Source URL file '{args.source_url}' is not valid JSON", file=sys.stderr)
                sys.exit(1)

        # Create a dictionary mapping IDs to video URLs
        url_dict = {}
        for item in url_data:
            if 'id' not in item:
                print(f"Warning: Item in source URL file is missing 'id' field: {item}", file=sys.stderr)
                continue

            if 'video_url' not in item:
                print(f"Warning: Item with ID '{item['id']}' is missing 'video_url' field", file=sys.stderr)
                continue

            url_dict[item['id']] = item['video_url']

        # 2. Process the schedule-source file
        with open(args.schedule_source, 'r', encoding='utf-8') as f:
            try:
                schedule_data = json.load(f)
            except json.JSONDecodeError:
                print(f"Error: Schedule source file '{args.schedule_source}' is not valid JSON", file=sys.stderr)
                sys.exit(1)

        # Update schedule items with video URLs
        for item in schedule_data:
            if 'id' not in item:
                print(f"Warning: Item in schedule file is missing 'id' field: {item}", file=sys.stderr)
                continue

            if item['id'] in url_dict:
                item['video_url'] = url_dict[item['id']]

        # 3. Write the result to the target file
        with open(args.schedule_target, 'w', encoding='utf-8') as f:
            json.dump(schedule_data, f, indent=2, ensure_ascii=False)

        print(f"Successfully merged video URLs into schedule. Output written to '{args.schedule_target}'")

    except Exception as e:
        print(f"Error: {str(e)}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
