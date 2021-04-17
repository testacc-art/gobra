// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2020 ETH Zurich.

package viper.gobra.translator.encodings

import org.bitbucket.inkytonik.kiama.==>
import viper.gobra.ast.{internal => in}
import viper.gobra.theory.Addressability.{Exclusive, Shared}
import viper.gobra.translator.Names
import viper.gobra.translator.interfaces.{Collector, Context}
import viper.gobra.translator.util.ViperWriter.CodeWriter
import viper.silver.{ast => vpr}

class IntEncoding extends LeafTypeEncoding {

  import viper.gobra.translator.util.ViperWriter.CodeLevel._
  import viper.gobra.translator.util.TypePatterns._

  /**
    * Translates a type into a Viper type.
    */
  override def typ(ctx: Context): in.Type ==> vpr.Type = {
    case ctx.Int() / m =>
      m match {
        case Exclusive => vpr.Int
        case Shared    => vpr.Ref
      }
  }

  /**
    * Encodes expressions as values that do not occupy some identifiable location in memory.
    *
    * To avoid conflicts with other encodings, a leaf encoding for type T should be defined at:
    * (1) exclusive operations on T, which includes literals and default values
    */
  override def expr(ctx: Context): in.Expr ==> CodeWriter[vpr.Exp] = {

    def goE(x: in.Expr): CodeWriter[vpr.Exp] = ctx.expr.translate(x)(ctx)

    default(super.expr(ctx)){
      case (e: in.DfltVal) :: ctx.Int() / Exclusive => unit(withSrc(vpr.IntLit(0), e))
      case lit: in.IntLit => unit(withSrc(vpr.IntLit(lit.v), lit))

      case e@ in.Add(l, r) => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.Add(vl, vr), e)
      case e@ in.Sub(l, r) => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.Sub(vl, vr), e)
      case e@ in.Mul(l, r) => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.Mul(vl, vr), e)
      case e@ in.Mod(l, r) => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.Mod(vl, vr), e)
      case e@ in.Div(l, r) => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.Div(vl, vr), e)

      case e@ in.BitwiseAnd(l, r) => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.FuncApp(bitwiseAnd, Seq(vl, vr)), e)
      case e@ in.BitwiseOr(l, r)  => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.FuncApp(bitwiseOr,  Seq(vl, vr)), e)
      case e@ in.BitwiseXor(l, r) => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.FuncApp(bitwiseXor, Seq(vl, vr)), e)
      case e@ in.BitClear(l, r)   => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.FuncApp(bitClear, Seq(vl, vr)), e)
      case e@ in.ShiftLeft(l, r)  => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.FuncApp(shiftLeft, Seq(vl, vr)), e)
      case e@ in.ShiftRight(l, r) => for {vl <- goE(l); vr <- goE(r)} yield withSrc(vpr.FuncApp(shiftRight, Seq(vl, vr)), e)
      case e@ in.BitwiseNeg(exp)  => for {ve <- goE(exp)} yield withSrc(vpr.FuncApp(bitwiseNegation, Seq(ve)), e)
    }
  }

  override def finalize(col: Collector): Unit = {
    col.addMember(bitwiseAnd)
    col.addMember(bitwiseOr)
    col.addMember(bitwiseXor)
    col.addMember(bitClear)
    col.addMember(shiftLeft)
    col.addMember(shiftRight)
    col.addMember(bitwiseNegation)
  }

  /* Bitwise Operations */
  private val bitwiseAnd: vpr.Function =
    vpr.Function(
      name = Names.bitwiseAnd,
      formalArgs = Seq(vpr.LocalVarDecl("left", vpr.Int)(), vpr.LocalVarDecl("right", vpr.Int)()),
      typ = vpr.Int,
      pres = Seq.empty,
      posts = Seq.empty,
      body = None
  )()

  private val bitwiseOr: vpr.Function =
    vpr.Function(
      name = Names.bitwiseOr,
      formalArgs = Seq(vpr.LocalVarDecl("left", vpr.Int)(), vpr.LocalVarDecl("right", vpr.Int)()),
      typ = vpr.Int,
      pres = Seq.empty,
      posts = Seq.empty,
      body = None
    )()

  private val bitwiseXor: vpr.Function =
    vpr.Function(
      name = Names.bitwiseXor,
      formalArgs = Seq(vpr.LocalVarDecl("left", vpr.Int)(), vpr.LocalVarDecl("right", vpr.Int)()),
      typ = vpr.Int,
      pres = Seq.empty,
      posts = Seq.empty,
      body = None
    )()

  private val bitClear: vpr.Function =
    vpr.Function(
      name = Names.bitClear,
      formalArgs = Seq(vpr.LocalVarDecl("left", vpr.Int)(), vpr.LocalVarDecl("right", vpr.Int)()),
      typ = vpr.Int,
      pres = Seq.empty,
      posts = Seq.empty,
      body = None
    )()

  private val shiftLeft: vpr.Function = {
    val left = vpr.LocalVarDecl("left", vpr.Int)()
    val right = vpr.LocalVarDecl("right", vpr.Int)()
    vpr.Function(
      name = Names.shiftLeft,
      formalArgs = Seq(left, right),
      typ = vpr.Int,
      // if the value at the right is < 0, it panicks
      pres = Seq(vpr.GeCmp(right.localVar, vpr.IntLit(BigInt(0))())()),
      posts = Seq.empty,
      body = None
    )()
  }

  private val shiftRight: vpr.Function = {
    val left = vpr.LocalVarDecl("left", vpr.Int)()
    val right = vpr.LocalVarDecl("right", vpr.Int)()
    vpr.Function(
      name = Names.shiftRight,
      formalArgs = Seq(left, right),
      typ = vpr.Int,
      // if the value at the right is < 0, it panicks
      pres = Seq(vpr.GeCmp(right.localVar, vpr.IntLit(BigInt(0))())()),
      posts = Seq.empty,
      body = None
    )()
  }

  private val bitwiseNegation: vpr.Function =
    vpr.Function(
      name = Names.bitwiseNeg,
      formalArgs = Seq(vpr.LocalVarDecl("exp", vpr.Int)()),
      typ = vpr.Int,
      pres = Seq.empty,
      posts = Seq.empty,
      body = None
    )()
}