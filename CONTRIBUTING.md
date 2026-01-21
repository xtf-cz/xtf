# Developing XTF

Compile from source:

```mvn clean install```

# XTF git development cycle <a name="devcycle"></a>
We use the [git flow](https://guides.github.com/introduction/flow/) workflow.
The steps are

0. Read the [guide](https://guides.github.com/).
1. Create an account [GitHub](https://github.com/).
2. Fork the [XTF](https://github.com/xtf-cz/xtf/) repository.
3. Clone your fork, add the upstream XTF repository as a remote, and check out locally:

        git clone git@github.com:USERNAME/xtf.git
        cd xtf
        git remote add upstream git@github.com:xtf-cz/xtf.git
        git branch main
        git checkout main
        git pull --rebase upstream main

   The steps until here only need to be executed once, with the exception being the last command: rebasing against upstream main branch.
   You need to rebase every time the upstream main is updated.

4. Create a feature branch

        git branch feature/BRANCH_NAME

5. Make the changes to the code (usually fixing a bug or adding a feature) 
6. **Ensure the code compiles without any error, tests pass and complies with the code style.**

        mvn clean test
        
    If jou need a code style check:
    
        mvn process-sources

    If something does not work, try to find out whether your change caused it, and why.
    Read error messages and use the internet to find solutions.
    Compilation errors are the easiest to fix!
    If all that does not help, ask us.
7. Commit with informative commit messages:

        git commit FILENAME(S) -m "Fix issue #1234"
        git commit FILENAME(S) -m "Add feature XYZ"

    The [amend option](https://help.github.com/articles/changing-a-commit-message/) is your friend if you are updating single commits (so that they appear as one)

        git commit --amend FILENAME(S)

    If you want to group say the last three commits as one, [squash](https://git-scm.com/book/en/v2/Git-Tools-Rewriting-History) them, for example

        git reset --soft HEAD~3
        git commit -m 'Clear commit message'
8. [Rebase](https://git-scm.com/book/en/v2/Git-Branching-Rebasing) against XTF's main branch.
    This might cause rebase errors, which you need to [solve](https://help.github.com/articles/resolving-merge-conflicts-after-a-git-rebase/)

        git pull --rebase upstream main

9. Push your commits to your fork

        git push origin feature/BRANCH_NAME

    If you squashed or amended commits after you had pushed already, you might be required to force push via using the `git push -f` option **with care**.

10. Send a [pull request](https://help.github.com/articles/about-pull-requests/) (PR) via GitHub.
    As described above, you can always **update** a pull request using the `git push -f` option. Please **do not** close and send new ones instead, always update.

## Requirements for merging your PR
 * Read some [tips](http://blog.ploeh.dk/2015/01/15/10-tips-for-better-pull-requests/) on how to write good pull requests.
    Make sure you don't waste your (and our) time by not respecting these basic rules.
 * All tests pass 
 * The PR is small in terms of line changes.
 * The PR is clean and addresses **one** issue.
 * The number of commits is minimal (i.e. one), the message is neat and clear.
 * For docs: clear, correct English language, spell-checked

# Automatic deploy to Maven repository

XTF is automatically built and deployed to maven repository when a feature branch is pushed into the upstream XTF repository. See `distributionManagement` [pom.xml](https://github.com/xtf-cz/xtf/blob/main/pom.xml)

In case you want to deploy into Maven repository anytime you push branch into your fork. You will need to configure github secrets
for maven repositories in your fork. Contact XTF admins for the details. 