package com.gtu.aiassistant.infrastructure.knowledge

object GtuLibraryKnowledgeSource {

    val PRIORITY_URLS: List<String> = listOf(
        "https://gtu.ge/en/Library/",
        "https://gtu.ge/en/Library/el-books/",
        "https://gtu.ge/en/Library/digital-library.php",
        "https://gtu.ge/en/Library/databases.php",
        "https://gtu.ge/en/Library/literature.php",
        "https://gtu.ge/en/Library/services",
        "https://gtu.ge/en/Library/Jurnalebi/",
        "https://gtu.ge/en/Library/Statiebi/",
        "https://gtu.ge/en/GTU/Public/acad-personal.php",
        "https://gtu.ge/stmm/en/about/structure/faculty-council.php",
        "https://gtu.ge/ims/en/about/structure/faculty-council.php",
        "https://gtu.ge/stmm/en/about/acad-personal.php",
        "https://gtu.ge/ims/en/about/acad-personal.php",
        "https://my.gtu.ge/en/faculties/"
    )

    val PRIORITY_OPAC_URLS: List<String> = listOf(
        "https://opac.gtu.ge/cgi-bin/koha/opac-main.pl",
        "https://opac.gtu.ge/cgi-bin/koha/opac-search.pl",
        "https://opac.gtu.ge/cgi-bin/koha/opac-authorities-home.pl",
        "https://opac.gtu.ge/cgi-bin/koha/opac-tags.pl",
        "https://opac.gtu.ge/cgi-bin/koha/opac-library.pl"
    )

    val FACULTY_HUB_URLS: List<String> = listOf(
        "https://my.gtu.ge/en/construction",
        "https://my.gtu.ge/en/pet",
        "https://my.gtu.ge/en/mining",
        "https://my.gtu.ge/en/ctmf",
        "https://my.gtu.ge/en/stmm",
        "https://my.gtu.ge/en/arch",
        "https://my.gtu.ge/en/law",
        "https://my.gtu.ge/en/bef",
        "https://my.gtu.ge/en/social",
        "https://my.gtu.ge/en/ims",
        "https://my.gtu.ge/en/ids",
        "https://my.gtu.ge/en/agro",
        "https://my.gtu.ge/en/mtis",
        "https://my.gtu.ge/en/med"
    )

    val FACULTY_ACADEMIC_PERSONNEL_URLS: List<String> = listOf(
        "https://gtu.ge/construction/en/about/acad-personal.php",
        "https://gtu.ge/pet/en/about/acad-personal.php",
        "https://gtu.ge/mining/en/about/acad-personal.php",
        "https://gtu.ge/ctmf/en/about/acad-personal.php",
        "https://gtu.ge/stmm/en/about/acad-personal.php",
        "https://gtu.ge/arch/en/about/acad-personal.php",
        "https://gtu.ge/law/en/about/acad-personal.php",
        "https://gtu.ge/ims/en/about/acad-personal.php",
        "https://gtu.ge/ids/about/academic-personal.php",
        "https://gtu.ge/bef/en/about/acad-personal.php",
        "https://gtu.ge/social/en/about/acad-personal.php"
    )

    val ALL_PRIORITY_URLS: List<String> = buildList {
        addAll(PRIORITY_URLS)
        addAll(PRIORITY_OPAC_URLS)
        addAll(FACULTY_HUB_URLS)
        addAll(FACULTY_ACADEMIC_PERSONNEL_URLS)
    }
}
