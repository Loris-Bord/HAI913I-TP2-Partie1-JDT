# TP2 Partie 1 : COmpréhension des programmes

Ce projet global implémente le TP2 Partie 1 de l'UE HAI913I de l'Université de Montpellier. 

## Contenu

Ce TP contient : 
- Un projet comprenant la réalisation du tp en utilisant uniquement l'API JDT
- Un projet comprenant la réalisation du tp en utilisant Spoon
- Ce README.md
- Un rapport de conception sur le développement du projet de compréhension de programme
- Une vidéo de démonstration du travail réalisé
- Un lien vers le dépot github des deux projets

## Pré-requis

Dans la classe ASTAnalyzer du projet avec JDT, il est nécessaire de modifier les attributs "projectPath" et "EXTRA_CLASSPATH" afin de renseigner respectivement le chemin d'accès vers le projet analysé (absolu) et le chemin vers le jar du JRE Java (absolu).

Dans la classe SpoonAnalyze du projet spoon, il est nécessaire de modifier la variable "projectSrc" dans le main afin d'avoir également un chemin (absolu) vers un projet disponible sur la machine qui teste. Il n'y a pas besoin de modifier les variables de classpath car Spoon ne les utilise pas ici.

## Lancement du projet

Lancer le main des projets via un IDE standard suffit à faire fonctionner les deux applications.

## Remarques diverses

- Tous les détails techniques relatifs à la conception et les concepts associés au calcul de couplage ou l'identification des modules, ainsi que du fonctionnement via jdt ou spoon (pour les besoins du TP) sont dans le rapport de projet.

## Auteur

Ce projet a été réalisé par : 

- Loris Bord

en Master 2 Génie Logiciel de l'Université de Montpellier.
